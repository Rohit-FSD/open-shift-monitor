package org.example.deploy.dto;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PodInfo {
    private String name;
    private String status;       // pod phase: Running, Pending, Failed, Succeeded, Unknown
    private boolean ready;
    private int restarts;
    private String node;
    private Instant created;     // pod metadata creationTimestamp
    private Instant startedAt;   // status.startTime — when scheduler picked up the pod
    private List<ContainerInfo> containers;

    @Data
    @Builder
    public static class ContainerInfo {
        private String name;
        private boolean ready;
        private String image;
        private String state;    // Running / Waiting:reason / Terminated:reason / Unknown
    }
}
