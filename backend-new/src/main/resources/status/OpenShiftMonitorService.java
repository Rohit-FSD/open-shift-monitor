package org.example.deploy.service;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deploy.config.HostConfig;
import org.example.deploy.config.OpenShiftProperties;
import org.example.deploy.dto.DeploymentInfo;
import org.example.deploy.dto.HealthStatus;
import org.example.deploy.dto.PodInfo;
import org.springframework.stereotype.Service;

/**
 * Reads deployment + pod state from OpenShift.
 *
 * Refactor highlights vs. previous version:
 *  - lastDeployedAt is sourced from the active ReplicaSet's creationTimestamp,
 *    which is the actual "this version went live" moment. Falls back to the
 *    Deployment's own creationTimestamp.
 *  - lastUpdated / pod created use real K8s metadata, not LocalDateTime.now().
 *  - One KubernetesClient is cached per envName instead of rebuilt per request.
 *  - extractVersion handles both `:tag` and `@sha256:digest` image refs.
 *  - Dead token fetch removed from getDeploymentStatusByEnv.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenShiftMonitorService {

    private final OpenShiftProperties openShiftProperties;
    private final HostConfig hostConfig;
    private final TokenService tokenService;

    /** envName -> client. Long-lived; only rebuilt on auth failure or invalidate(). */
    private final Map<String, KubernetesClient> clientCache = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /** Get deployment status by environment name (preferred entry point). */
    public List<DeploymentInfo> getDeploymentStatusByEnv(String envName) {
        log.info("Getting deployment status for environment: {}", envName);
        OpenShiftProperties.EnvDetails envDetails = requireEnv(envName);
        KubernetesClient client = getOrCreateClient(envName);
        return getDeployments(client, envDetails.getNamespace());
    }

    /** Backward-compatible: look up the env that owns this namespace. */
    @Deprecated
    public List<DeploymentInfo> getDeploymentStatus(String namespace) {
        log.info("Getting deployment status for namespace: {} (legacy method)", namespace);
        String envName = openShiftProperties.getEnvironments().entrySet().stream()
                .filter(e -> e.getValue().getNamespace().equals(namespace))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No environment found for namespace: " + namespace));
        return getDeploymentStatusByEnv(envName);
    }

    public List<String> getAllNamespaces() {
        return openShiftProperties.getEnvironments().values().stream()
                .map(OpenShiftProperties.EnvDetails::getNamespace)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /** Force the next call for this env to re-authenticate (used on 401). */
    public void invalidateClient(String envName) {
        KubernetesClient old = clientCache.remove(envName);
        if (old != null) {
            try { old.close(); } catch (Exception ignored) { }
        }
    }

    @PreDestroy
    void shutdown() {
        clientCache.values().forEach(c -> {
            try { c.close(); } catch (Exception ignored) { }
        });
        clientCache.clear();
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private List<DeploymentInfo> getDeployments(KubernetesClient client, String namespace) {
        log.info("Fetching deployments from namespace: {}", namespace);
        DeploymentList list = client.apps().deployments().inNamespace(namespace).list();

        List<DeploymentInfo> out = new ArrayList<>(list.getItems().size());
        for (Deployment d : list.getItems()) {
            try {
                out.add(mapToDeploymentInfo(d, namespace, client));
            } catch (Exception e) {
                log.warn("Skipping deployment {} due to mapping error: {}",
                        d.getMetadata() != null ? d.getMetadata().getName() : "?",
                        e.getMessage());
            }
        }
        log.info("Found {} deployments in namespace: {}", out.size(), namespace);
        return out;
    }

    private DeploymentInfo mapToDeploymentInfo(Deployment d, String namespace, KubernetesClient client) {
        String name = d.getMetadata().getName();

        List<Container> containers = d.getSpec().getTemplate().getSpec().getContainers();

        // Container name -> version (LinkedHashMap preserves spec order)
        Map<String, String> containerVersions = new LinkedHashMap<>();
        for (Container c : containers) {
            containerVersions.put(c.getName(), extractVersion(c.getImage()));
        }
        String primaryImage = containers.get(0).getImage();
        String primaryVersion = extractVersion(primaryImage);

        Integer replicas = nz(d.getSpec().getReplicas());
        Integer readyReplicas = d.getStatus() != null ? nz(d.getStatus().getReadyReplicas()) : 0;
        Integer availableReplicas = d.getStatus() != null ? nz(d.getStatus().getAvailableReplicas()) : 0;

        HealthStatus status = determineHealthStatus(replicas, readyReplicas, availableReplicas);

        // Pods
        List<Pod> pods = client.pods().inNamespace(namespace)
                .withLabels(d.getSpec().getSelector().getMatchLabels())
                .list().getItems();
        List<PodInfo> podInfos = pods.stream().map(this::mapToPodInfo).collect(Collectors.toList());

        // Revision + last-deployed timestamp
        Long revision = parseRevision(d);
        Instant lastDeployedAt = findActiveReplicaSet(client, namespace, d, revision)
                .map(rs -> parseInstant(rs.getMetadata().getCreationTimestamp()))
                .orElseGet(() -> parseInstant(d.getMetadata().getCreationTimestamp()));

        // lastUpdated reflects when the Deployment's status was last observed by K8s
        Instant lastUpdated = d.getStatus() != null && d.getStatus().getConditions() != null
                ? d.getStatus().getConditions().stream()
                    .map(c -> parseInstant(c.getLastUpdateTime()))
                    .filter(t -> t != null)
                    .max(Comparator.naturalOrder())
                    .orElse(lastDeployedAt)
                : lastDeployedAt;

        return DeploymentInfo.builder()
                .name(name)
                .namespace(namespace)
                .replicas(replicas)
                .readyReplicas(readyReplicas)
                .availableReplicas(availableReplicas)
                .image(primaryImage)
                .version(primaryVersion)
                .containerVersions(containerVersions)
                .status(status)
                .pods(podInfos)
                .revision(revision)
                .lastDeployedAt(lastDeployedAt)
                .lastUpdated(lastUpdated)
                .build();
    }

    private PodInfo mapToPodInfo(Pod pod) {
        String podName = pod.getMetadata().getName();
        String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown";
        String node = pod.getSpec() != null ? pod.getSpec().getNodeName() : null;

        boolean ready = false;
        if (pod.getStatus() != null && pod.getStatus().getConditions() != null) {
            ready = pod.getStatus().getConditions().stream()
                    .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
        }

        int restarts = 0;
        List<PodInfo.ContainerInfo> containerInfos = new ArrayList<>();
        if (pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null) {
            for (ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                restarts += cs.getRestartCount() != null ? cs.getRestartCount() : 0;
                containerInfos.add(PodInfo.ContainerInfo.builder()
                        .name(cs.getName())
                        .ready(Boolean.TRUE.equals(cs.getReady()))
                        .image(cs.getImage())
                        .state(getContainerState(cs.getState()))
                        .build());
            }
        }

        Instant created = parseInstant(pod.getMetadata().getCreationTimestamp());
        Instant startedAt = pod.getStatus() != null
                ? parseInstant(pod.getStatus().getStartTime())
                : null;

        return PodInfo.builder()
                .name(podName)
                .status(phase)
                .ready(ready)
                .restarts(restarts)
                .node(node)
                .created(created)
                .startedAt(startedAt)
                .containers(containerInfos)
                .build();
    }

    /**
     * Returns the ReplicaSet matching the deployment's current revision.
     * That RS's creationTimestamp = when the current image rolled out.
     */
    private Optional<ReplicaSet> findActiveReplicaSet(KubernetesClient client, String namespace,
                                                      Deployment d, Long revision) {
        try {
            Map<String, String> selector = d.getSpec().getSelector().getMatchLabels();
            List<ReplicaSet> rsList = client.apps().replicaSets()
                    .inNamespace(namespace)
                    .withLabels(selector)
                    .list().getItems();

            if (revision != null) {
                Optional<ReplicaSet> match = rsList.stream()
                        .filter(rs -> revision.equals(parseRevision(rs)))
                        .findFirst();
                if (match.isPresent()) return match;
            }
            // Fallback: pick the newest RS that has at least one replica
            return rsList.stream()
                    .filter(rs -> rs.getSpec() != null
                            && rs.getSpec().getReplicas() != null
                            && rs.getSpec().getReplicas() > 0)
                    .max(Comparator.comparing(rs -> parseInstant(rs.getMetadata().getCreationTimestamp()),
                            Comparator.nullsFirst(Comparator.naturalOrder())));
        } catch (Exception e) {
            log.debug("ReplicaSet lookup failed for {}: {}", d.getMetadata().getName(), e.getMessage());
            return Optional.empty();
        }
    }

    private String getContainerState(ContainerState state) {
        if (state == null) return "Unknown";
        if (state.getRunning() != null) return "Running";
        if (state.getWaiting() != null) {
            String reason = state.getWaiting().getReason();
            return reason != null ? "Waiting: " + reason : "Waiting";
        }
        if (state.getTerminated() != null) {
            String reason = state.getTerminated().getReason();
            return reason != null ? "Terminated: " + reason : "Terminated";
        }
        return "Unknown";
    }

    private String extractVersion(String image) {
        if (image == null || image.isEmpty()) return "unknown";
        int at = image.indexOf('@');
        if (at > 0) {
            // image@sha256:abcd... — return short digest
            String digest = image.substring(at + 1);
            int colon = digest.indexOf(':');
            String hash = colon > 0 ? digest.substring(colon + 1) : digest;
            return "sha256:" + hash.substring(0, Math.min(12, hash.length()));
        }
        int colon = image.lastIndexOf(':');
        // guard against registry:port without tag
        int slash = image.lastIndexOf('/');
        if (colon < 0 || colon < slash) return "latest";
        return image.substring(colon + 1);
    }

    private HealthStatus determineHealthStatus(int replicas, int ready, int available) {
        if (replicas == 0) return HealthStatus.UNKNOWN;
        if (ready == 0) return HealthStatus.DOWN;
        if (ready < replicas || available < replicas) return HealthStatus.DEGRADED;
        return HealthStatus.HEALTHY;
    }

    private Long parseRevision(io.fabric8.kubernetes.api.model.HasMetadata obj) {
        if (obj.getMetadata() == null || obj.getMetadata().getAnnotations() == null) return null;
        String s = obj.getMetadata().getAnnotations().get("deployment.kubernetes.io/revision");
        try {
            return s != null ? Long.parseLong(s) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }

    private static int nz(Integer v) { return v == null ? 0 : v; }

    private OpenShiftProperties.EnvDetails requireEnv(String envName) {
        OpenShiftProperties.EnvDetails env = openShiftProperties.getEnvironments().get(envName);
        if (env == null) {
            throw new IllegalArgumentException("Invalid environment: " + envName
                    + ". Available: " + openShiftProperties.getEnvironments().keySet());
        }
        return env;
    }

    // ---------------------------------------------------------------------
    // Client cache
    // ---------------------------------------------------------------------

    private KubernetesClient getOrCreateClient(String envName) {
        return clientCache.computeIfAbsent(envName, this::buildClient);
    }

    private KubernetesClient buildClient(String envName) {
        OpenShiftProperties.EnvDetails envDetails = requireEnv(envName);
        String host = hostConfig.getHost(
                envDetails.getVersion(),
                envDetails.getCluster(),
                envDetails.getRealm());
        String token = tokenService.getTokenValue(envDetails.getSystemAccount());

        Config config = new ConfigBuilder()
                .withMasterUrl(host)
                .withOauthToken(token)
                .withTrustCerts(true)
                .withConnectionTimeout(30000)
                .withRequestTimeout(30000)
                .build();
        return new KubernetesClientBuilder().withConfig(config).build();
    }
}
