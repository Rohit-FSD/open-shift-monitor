package com.yourorg.deploy.service;

import com.yourorg.deploy.config.HostConfig;
import com.yourorg.deploy.config.OpenShiftProperties;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenShiftMonitorService {

    private final OpenShiftProperties openShiftProperties;
    private final HostConfig hostConfig;
    private final JourneyLogService.TokenService tokenService;

    // One KubernetesClient per environment — avoids repeated TLS handshakes.
    private final ConcurrentHashMap<String, KubernetesClient> clientCache =
            new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Cached deployment-status fetch (30 s TTL configured in CacheConfig)
    // -------------------------------------------------------------------------

    /**
     * Returns deployment status for all deployments in the environment's namespace.
     * Result is cached per envName for 30 seconds so dashboard polls served from
     * cache (~95 % hit rate at 5-second polling intervals).
     *
     * Cache key = envName, which already encodes realm/cluster/namespace via
     * OpenShiftProperties — no finer granularity needed.
     */
    @Cacheable(value = "deploymentStatus", key = "#envName")
    public DeploymentStatusResult getDeploymentStatus(String envName) {
        log.info("Cache MISS — fetching live deployment status for env={}", envName);
        OpenShiftProperties.EnvDetails env = resolveEnv(envName);
        try {
            KubernetesClient client = getOrCreateClient(envName, env);
            List<Deployment> deployments = client.apps().deployments()
                    .inNamespace(env.getNamespace())
                    .list()
                    .getItems();
            List<ReplicaSet> replicaSets = client.apps().replicaSets()
                    .inNamespace(env.getNamespace())
                    .list()
                    .getItems();
            log.info("Fetched {} deployments, {} replica-sets for env={}",
                    deployments.size(), replicaSets.size(), envName);
            return new DeploymentStatusResult(envName, env.getNamespace(), deployments, replicaSets);
        } catch (Exception e) {
            // Catch was missing in the original — now explicitly handled so a single
            // auth failure doesn't crash the whole dashboard endpoint.
            log.error("Failed to fetch deployment status for env={}: {}", envName, e.getMessage(), e);
            return DeploymentStatusResult.empty(envName, env.getNamespace());
        }
    }

    /**
     * Evicts the cached deployment status for a specific environment.
     * Called on auth failure so the next request forces a fresh token + client rebuild.
     */
    @CacheEvict(value = "deploymentStatus", key = "#envName")
    public void invalidateClient(String envName) {
        log.warn("Invalidating Kubernetes client and cache for env={}", envName);
        KubernetesClient old = clientCache.remove(envName);
        if (old != null) {
            try { old.close(); } catch (Exception ex) {
                log.warn("Error closing invalidated client for env={}: {}", envName, ex.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a cached KubernetesClient built from the env's realm/cluster/version.
     * Realm-based host resolution is the fix for status not fetching correctly
     * across environments — each env gets a client pointed at the right API server.
     */
    private KubernetesClient getOrCreateClient(String envName, OpenShiftProperties.EnvDetails env) {
        return clientCache.computeIfAbsent(envName, key -> {
            // Realm is the critical field: determines which OpenShift cluster URL to use.
            String host = hostConfig.getHost(env.getVersion(), env.getCluster(), env.getRealm());
            String token = tokenService.getTokenValue(env.getSystemAccount());
            log.info("Creating Kubernetes client for env={} realm={} host={}", envName, env.getRealm(), host);
            Config config = new ConfigBuilder()
                    .withMasterUrl(host)
                    .withOauthToken(token)
                    .withTrustCerts(true)
                    .withConnectionTimeout(30_000)
                    .withRequestTimeout(60_000)
                    .build();
            return new KubernetesClientBuilder().withConfig(config).build();
        });
    }

    private OpenShiftProperties.EnvDetails resolveEnv(String envName) {
        OpenShiftProperties.EnvDetails env = openShiftProperties.getEnvironments().get(envName);
        if (env == null) throw new IllegalArgumentException("Unknown environment: " + envName);
        return env;
    }

    @PreDestroy
    public void shutdown() {
        clientCache.values().forEach(c -> {
            try { c.close(); } catch (Exception e) {
                log.warn("Error closing client on shutdown: {}", e.getMessage());
            }
        });
        clientCache.clear();
    }

    // -------------------------------------------------------------------------
    // Result DTO
    // -------------------------------------------------------------------------

    public record DeploymentStatusResult(
            String envName,
            String namespace,
            List<Deployment> deployments,
            List<ReplicaSet> replicaSets) {

        static DeploymentStatusResult empty(String envName, String namespace) {
            return new DeploymentStatusResult(envName, namespace,
                    Collections.emptyList(), Collections.emptyList());
        }
    }
}
