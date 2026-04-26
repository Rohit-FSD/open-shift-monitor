package org.example.deploy.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * One Deployment's status snapshot for the dashboard.
 *
 * lastDeployedAt = when the current ReplicaSet was created (i.e. when this
 *   image actually went live). Use this for "Deployed 2h ago" UI.
 * lastUpdated    = most recent K8s status condition transition (controller
 *   activity), useful for diagnostics but NOT what users mean by "deployed".
 * revision       = deployment.kubernetes.io/revision, increments on each
 *   spec change.
 */
@Data
@Builder
public class DeploymentInfo {
    private String name;
    private String namespace;

    private Integer replicas;
    private Integer readyReplicas;
    private Integer availableReplicas;

    private String image;
    private String version;
    private Map<String, String> containerVersions;

    private HealthStatus status;
    private List<PodInfo> pods;

    private Long revision;
    private Instant lastDeployedAt;
    private Instant lastUpdated;
}
