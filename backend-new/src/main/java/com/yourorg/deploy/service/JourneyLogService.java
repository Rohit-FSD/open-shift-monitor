package com.yourorg.deploy.service;

import com.yourorg.deploy.config.HostConfig;
import com.yourorg.deploy.config.OpenShiftProperties;
import com.yourorg.deploy.dto.DownstreamCallsResponse;
import com.yourorg.deploy.model.DownstreamApiCall;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JourneyLogService {

    private final OpenShiftProperties openShiftProperties;
    private final HostConfig hostConfig;
    private final TokenService tokenService;
    private final DownstreamCallParserService downstreamCallParserService;

    // One fixed-size pool for all parallel pod-log fetches (IO-bound).
    // 20 threads covers 10 pods × 2 containers in one round-trip.
    private static final ExecutorService POD_FETCH_EXECUTOR =
            Executors.newFixedThreadPool(20);

    // KubernetesClient is expensive to create (OkHttp connection pool + TLS).
    // Cache one per environment so we reuse connections across requests.
    private final ConcurrentHashMap<String, KubernetesClient> clientCache =
            new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Public API: raw log fetching (used by LogAnalyticsService for success rate)
    // -------------------------------------------------------------------------

    /**
     * Returns raw concatenated logs for ALL pods in the environment namespace.
     * This is the entry point for success-rate log analysis.
     */
    public String getAllLogsForEnvironment(String envName, int timeRangeMinutes) {
        log.info("Fetching ALL logs for environment: {} (last {} minutes)", envName, timeRangeMinutes);
        OpenShiftProperties.EnvDetails envDetails = resolveEnv(envName);
        String namespace = envDetails.getNamespace();
        try {
            KubernetesClient client = getOrCreateClient(envName);
            List<Pod> pods = getAllPods(client, namespace);
            log.info("Found {} pods in environment {}", pods.size(), envName);
            return fetchLogsForPods(pods, client, namespace, timeRangeMinutes);
        } catch (Exception e) {
            log.error("Error fetching logs for environment {}: {}", envName, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch logs for environment: " + envName, e);
        }
    }

    /**
     * Returns raw concatenated logs for all pods belonging to a specific service.
     */
    public String getServiceLogs(String envName, String serviceName, int timeRangeMinutes) {
        log.info("Fetching logs for service: {} in environment: {} (last {} minutes)",
                serviceName, envName, timeRangeMinutes);
        OpenShiftProperties.EnvDetails envDetails = resolveEnv(envName);
        String namespace = envDetails.getNamespace();
        try {
            KubernetesClient client = getOrCreateClient(envName);
            List<Pod> pods = findPodsForService(client, namespace, serviceName);
            log.info("Found {} pods for service {}", pods.size(), serviceName);
            return fetchLogsForPods(pods, client, namespace, timeRangeMinutes);
        } catch (Exception e) {
            log.error("Error fetching logs for service {}: {}", serviceName, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch logs for service: " + serviceName, e);
        }
    }

    /**
     * Fetches both pod status snapshots and concatenated logs for a service in a single
     * kube-client session. Used by log-failure detection which needs BOTH:
     *  - Pod status (for POD_FAILURE classification)
     *  - Log text      (for APPLICATION_ERROR / DOWNSTREAM_FAILURE classification)
     */
    public ServiceSnapshot getServiceSnapshot(String envName, String serviceName, int timeRangeMinutes) {
        OpenShiftProperties.EnvDetails envDetails = resolveEnv(envName);
        String namespace = envDetails.getNamespace();
        try {
            KubernetesClient client = getOrCreateClient(envName);
            List<Pod> pods = findPodsForService(client, namespace, serviceName);
            String logs = fetchLogsForPods(pods, client, namespace, timeRangeMinutes);
            return ServiceSnapshot.builder()
                    .namespace(namespace)
                    .pods(pods)
                    .rawLogs(logs)
                    .build();
        } catch (Exception e) {
            log.error("Error fetching snapshot for service {}: {}", serviceName, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch snapshot for service: " + serviceName, e);
        }
    }

    // -------------------------------------------------------------------------
    // Public API: journey / correlation-ID search (separate from success rate)
    // -------------------------------------------------------------------------

    /**
     * Searches logs for a specific TID or Correlation ID across pods.
     * Returns a structured response with per-pod match details.
     * NOTE: this is for the Journey Log Search feature, not success-rate calculation.
     */
    public JourneyLogsResponse getJourneyLogs(String envName, String searchId,
                                               String serviceName, Integer timeRangeMinutes) {
        log.info("Journey log search — env={} id={} service={} minutes={}",
                envName, searchId, serviceName != null ? serviceName : "ALL", timeRangeMinutes);

        OpenShiftProperties.EnvDetails envDetails = resolveEnv(envName);
        String namespace = envDetails.getNamespace();

        try {
            KubernetesClient client = getOrCreateClient(envName);
            List<Pod> pods = serviceName != null
                    ? findPodsForService(client, namespace, serviceName)
                    : getAllPods(client, namespace);

            log.info("Found {} pods to search", pods.size());

            // Build one task per (pod, container) and fetch all logs in parallel.
            List<CompletableFuture<Optional<PodLogEntry>>> futures = new ArrayList<>();
            for (Pod pod : pods) {
                String podName = pod.getMetadata().getName();
                for (Container container : pod.getSpec().getContainers()) {
                    String containerName = container.getName();
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        String logs = fetchContainerLogs(client, namespace, podName,
                                containerName, timeRangeMinutes);
                        if (logs == null || logs.isEmpty()) return Optional.empty();
                        List<String> matchingLines = grepLogs(logs, searchId);
                        if (matchingLines.isEmpty()) return Optional.empty();
                        long directMatches = matchingLines.stream()
                                .filter(l -> l.startsWith(">>>")).count();
                        return Optional.of(PodLogEntry.builder()
                                .podName(podName)
                                .containerName(containerName)
                                .serviceName(extractServiceName(podName))
                                .matchingLines(matchingLines)
                                .totalMatches((int) directMatches)
                                .build());
                    }, POD_FETCH_EXECUTOR));
                }
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<PodLogEntry> logEntries = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());

            int totalMatches = logEntries.stream().mapToInt(PodLogEntry::getTotalMatches).sum();
            log.info("Search complete — pods={} matches={}", pods.size(), totalMatches);

            return JourneyLogsResponse.builder()
                    .searchId(searchId)
                    .searchType(detectSearchType(searchId))
                    .environment(envName)
                    .namespace(namespace)
                    .searchedService(serviceName)
                    .timeRangeMinutes(timeRangeMinutes != null ? timeRangeMinutes : 0)
                    .totalPodsSearched(pods.size())
                    .totalMatchingLogs(totalMatches)
                    .podLogs(logEntries)
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("ERROR during log search: {}", e.getMessage(), e);
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // Public API: downstream call extraction
    // -------------------------------------------------------------------------

    /**
     * Fetches raw logs for all (or one) service's pods, then delegates to
     * {@link DownstreamCallParserService} to extract structured request/response
     * pairs from the DEBUG-mode downstream HTTP call lines.
     */
    public DownstreamCallsResponse getDownstreamCalls(
            String envName, String searchId,
            String serviceName, Integer timeRangeMinutes) {

        log.info("DOWNSTREAM CALL EXTRACTION — env={} id={} service={} minutes={}",
                envName, searchId, serviceName, timeRangeMinutes);

        OpenShiftProperties.EnvDetails envDetails = resolveEnv(envName);
        String namespace = envDetails.getNamespace();

        try {
            KubernetesClient client = getOrCreateClient(envName);
            List<Pod> pods = serviceName != null
                    ? findPodsForService(client, namespace, serviceName)
                    : getAllPods(client, namespace);

            // Fetch and parse each (pod, container) in parallel — independent operations.
            List<CompletableFuture<List<DownstreamApiCall>>> futures = new ArrayList<>();
            for (Pod pod : pods) {
                String podName = pod.getMetadata().getName();
                for (Container container : pod.getSpec().getContainers()) {
                    String containerName = container.getName();
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        String logs = fetchContainerLogs(client, namespace, podName,
                                containerName, timeRangeMinutes);
                        if (logs == null || logs.isEmpty()) return Collections.emptyList();
                        return downstreamCallParserService.parseDownstreamCalls(logs, searchId, podName);
                    }, POD_FETCH_EXECUTOR));
                }
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<DownstreamApiCall> allCalls = futures.stream()
                    .flatMap(f -> f.join().stream())
                    .collect(Collectors.toList());

            // Sort chronologically across all pods
            allCalls.sort(Comparator.comparing(DownstreamApiCall::getRequestTimestamp,
                    Comparator.nullsLast(Comparator.naturalOrder())));

            long successCount = allCalls.stream()
                    .filter(c -> "SUCCESS".equals(c.getCallStatus())).count();
            // Any non-SUCCESS terminal status counts as an error (CLIENT_ERROR,
            // SERVER_ERROR, TIMEOUT, CONN_ERROR).
            long errorCount = allCalls.stream()
                    .filter(c -> c.getCallStatus() != null
                            && !"SUCCESS".equals(c.getCallStatus())).count();

            log.info("Downstream extraction done — total={} success={} error={}",
                    allCalls.size(), successCount, errorCount);

            return DownstreamCallsResponse.builder()
                    .searchId(searchId)
                    .environment(envName)
                    .namespace(namespace)
                    .totalPodsSearched(pods.size())
                    .totalCalls(allCalls.size())
                    .successCount((int) successCount)
                    .errorCount((int) errorCount)
                    .calls(allCalls)
                    .timestamp(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("ERROR during downstream extraction: {}", e.getMessage(), e);
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Core shared method — iterates pods → containers → fetchContainerLogs,
     * appending all raw log text. Used by both getAllLogsForEnvironment and getServiceLogs.
     */
    private String fetchLogsForPods(List<Pod> pods, KubernetesClient client,
                                     String namespace, Integer timeRangeMinutes) {
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (Pod pod : pods) {
            String podName = pod.getMetadata().getName();
            for (Container container : pod.getSpec().getContainers()) {
                String containerName = container.getName();
                futures.add(CompletableFuture.supplyAsync(
                        () -> fetchContainerLogs(client, namespace, podName, containerName, timeRangeMinutes),
                        POD_FETCH_EXECUTOR));
            }
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        String result = futures.stream()
                .map(CompletableFuture::join)
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.joining("\n"));
        log.info("Fetched {} bytes of logs from {} pods", result.length(), pods.size());
        return result;
    }

    // Hard cap: never fetch more than this many seconds of history in one call.
    // Prevents GB-scale reads on long-running pods when no timeRange is specified.
    private static final int DEFAULT_LOG_WINDOW_SECONDS = 60 * 60; // 1 hour

    private String fetchContainerLogs(KubernetesClient client, String namespace,
                                       String podName, String containerName, Integer timeRangeMinutes) {
        try {
            int windowSeconds = (timeRangeMinutes != null && timeRangeMinutes > 0)
                    ? timeRangeMinutes * 60
                    : DEFAULT_LOG_WINDOW_SECONDS;

            log.debug("Fetching logs: pod={} container={} windowSeconds={}", podName, containerName, windowSeconds);

            String logs = client.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .inContainer(containerName)
                    .sinceSeconds(windowSeconds)
                    .getLog();

            if (logs == null) {
                log.warn("Null logs returned for container: {}", containerName);
                return "";
            }
            return logs;
        } catch (Exception e) {
            log.error("Failed to fetch logs for {}/{}: {}", podName, containerName, e.getMessage(), e);
            return "";
        }
    }

    private List<Pod> getAllPods(KubernetesClient client, String namespace) {
        log.info("Fetching ALL pods in namespace: {}", namespace);
        List<Pod> pods = client.pods()
                .inNamespace(namespace)
                .list()
                .getItems();
        log.info("Found {} total pods", pods.size());
        return pods;
    }

    private List<Pod> findPodsForService(KubernetesClient client, String namespace, String serviceName) {
        log.info("Searching for pods with label: app={}", serviceName);
        List<Pod> pods = client.pods()
                .inNamespace(namespace)
                .withLabel("app", serviceName)
                .list()
                .getItems();

        if (pods.isEmpty()) {
            log.warn("No pods found with label app={}, trying app.kubernetes.io/name", serviceName);
            pods = client.pods()
                    .inNamespace(namespace)
                    .withLabel("app.kubernetes.io/name", serviceName)
                    .list()
                    .getItems();
        }

        // Final fallback: match by pod-name prefix (e.g. "bcp-css-service-5f7b9d6867-855sc").
        // A Deployment pod is always "<deploy>-<rs-hash>-<pod-hash>" — exactly two
        // alphanumeric chunks after the service name. This rejects sibling deployments
        // that share a prefix (e.g. "bcp-idv-service-nfr-..." should NOT match
        // serviceName="bcp-idv-service").
        if (pods.isEmpty()) {
            log.warn("No labeled pods found, falling back to pod-name prefix match for: {}", serviceName);
            String prefix = serviceName + "-";
            java.util.regex.Pattern tail = java.util.regex.Pattern.compile("^[a-z0-9]+-[a-z0-9]+$");
            pods = client.pods()
                    .inNamespace(namespace)
                    .list()
                    .getItems()
                    .stream()
                    .filter(p -> p.getMetadata() != null && p.getMetadata().getName() != null)
                    .filter(p -> {
                        String name = p.getMetadata().getName();
                        if (!name.startsWith(prefix)) return false;
                        return tail.matcher(name.substring(prefix.length())).matches();
                    })
                    .collect(Collectors.toList());
        }

        log.info("Found {} pods for service: {}", pods.size(), serviceName);
        return pods;
    }

    /**
     * Returns matching lines with 2-line context. Direct matches are prefixed ">>> ".
     * Used only by getJourneyLogs (journey search) — NOT for success-rate filter evaluation.
     */
    private List<String> grepLogs(String logs, String searchId) {
        List<String> allLines = logs.lines().collect(Collectors.toList());
        List<String> matches = new ArrayList<>();

        Pattern pattern = Pattern.compile(".*" + Pattern.quote(searchId) + ".*",
                Pattern.CASE_INSENSITIVE);

        for (int i = 0; i < allLines.size(); i++) {
            if (pattern.matcher(allLines.get(i)).matches()) {
                int start = Math.max(0, i - 2);
                int end = Math.min(allLines.size(), i + 3);
                if (start > 0 && !matches.isEmpty()) matches.add("---");
                for (int j = start; j < end; j++) {
                    matches.add((j == i ? ">>> " : "    ") + allLines.get(j));
                }
            }
        }
        log.debug("Grep: Pattern={}, Total lines={}, Matches={}", searchId, allLines.size(), matches.size());
        return matches;
    }

    private String extractServiceName(String podName) {
        int lastDash = podName.lastIndexOf('-');
        if (lastDash > 0) {
            int secondLastDash = podName.lastIndexOf('-', lastDash - 1);
            if (secondLastDash > 0) return podName.substring(0, secondLastDash);
        }
        return podName;
    }

    private String detectSearchType(String searchId) {
        // Both TIDs and Correlation IDs are UUIDs — cannot distinguish by format alone
        return "Transaction/Correlation ID";
    }

    private OpenShiftProperties.EnvDetails resolveEnv(String envName) {
        OpenShiftProperties.EnvDetails envDetails = openShiftProperties.getEnvironments().get(envName);
        if (envDetails == null) throw new IllegalArgumentException("Invalid environment: " + envName);
        return envDetails;
    }

    /**
     * Returns a cached KubernetesClient for the given environment.
     * Creating a client is expensive (OkHttp pool + TLS handshake), so we keep one
     * per environment for the lifetime of the process. Tokens are long-lived service
     * account tokens in OpenShift; if a token rotates, restart the app to clear the cache.
     */
    private KubernetesClient getOrCreateClient(String envName) {
        return clientCache.computeIfAbsent(envName, env -> {
            OpenShiftProperties.EnvDetails envDetails = openShiftProperties.getEnvironments().get(env);
            String host = hostConfig.getHost(envDetails.getVersion(), envDetails.getCluster(), envDetails.getRealm());
            String token = tokenService.getTokenValue(envDetails.getSystemAccount());
            log.info("Creating (and caching) Kubernetes client for env={} host={}", env, host);
            Config config = new ConfigBuilder()
                    .withMasterUrl(host)
                    .withOauthToken(token)
                    .withTrustCerts(true)
                    .withConnectionTimeout(30000)
                    .withRequestTimeout(60000)
                    .build();
            return new KubernetesClientBuilder().withConfig(config).build();
        });
    }

    // -------------------------------------------------------------------------
    // DTOs
    // -------------------------------------------------------------------------

    @Data
    @Builder
    public static class JourneyLogsResponse {
        private String searchId;
        private String searchType;
        private String environment;
        private String namespace;
        private String searchedService;
        private int timeRangeMinutes;
        private int totalPodsSearched;
        private int totalMatchingLogs;
        private List<PodLogEntry> podLogs;
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    public static class ServiceSnapshot {
        private String namespace;
        private List<Pod> pods;
        private String rawLogs;
    }

    @Data
    @Builder
    public static class PodLogEntry {
        private String podName;
        private String containerName;
        private String serviceName;
        private List<String> matchingLines;
        private int totalMatches;
    }

    // Placeholder interfaces — replace with your actual beans
    public interface TokenService {
        String getTokenValue(String systemAccount);
    }
}
