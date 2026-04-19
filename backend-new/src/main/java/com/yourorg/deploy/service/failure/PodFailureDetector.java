package com.yourorg.deploy.service.failure;

import com.yourorg.deploy.dto.LogFailureDetectionResponse.PodFailureDetail;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStateWaiting;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Classifies pod-level failures from Kubernetes status, not logs.
 * Triggered reasons below map to the generic "contact DevOps" recommendation.
 */
@Component
public class PodFailureDetector {

    private static final String RECOMMENDATION = "Contact DevOps — pod is in a failing state";

    private static final Set<String> BAD_WAITING = Set.of(
            "ImagePullBackOff", "ErrImagePull", "CrashLoopBackOff", "CreateContainerConfigError"
    );
    private static final Set<String> BAD_TERMINATED = Set.of("OOMKilled", "Error");
    private static final int RESTART_THRESHOLD = 5;

    public List<PodFailureDetail> detect(List<Pod> pods) {
        List<PodFailureDetail> out = new ArrayList<>();
        if (pods == null) return out;

        for (Pod pod : pods) {
            if (pod.getStatus() == null) continue;
            String podName = pod.getMetadata() != null ? pod.getMetadata().getName() : "unknown";
            String phase = pod.getStatus().getPhase();

            String reason = null;
            String message = null;
            int restarts = 0;

            if ("Failed".equalsIgnoreCase(phase)) {
                reason = "PodPhase=Failed";
                message = pod.getStatus().getMessage();
            }

            List<ContainerStatus> statuses = pod.getStatus().getContainerStatuses();
            if (statuses != null) {
                for (ContainerStatus cs : statuses) {
                    restarts = Math.max(restarts, cs.getRestartCount() == null ? 0 : cs.getRestartCount());
                    if (cs.getState() != null) {
                        ContainerStateWaiting w = cs.getState().getWaiting();
                        if (w != null && BAD_WAITING.contains(w.getReason())) {
                            reason = w.getReason();
                            message = w.getMessage();
                        }
                        ContainerStateTerminated t = cs.getState().getTerminated();
                        if (t != null && BAD_TERMINATED.contains(t.getReason())) {
                            reason = t.getReason();
                            message = t.getMessage();
                        }
                    }
                }
            }

            if (reason == null && restarts > RESTART_THRESHOLD) {
                reason = "HighRestartCount";
                message = "Container has restarted " + restarts + " times";
            }

            if (reason == null) continue;

            out.add(PodFailureDetail.builder()
                    .podName(podName)
                    .reason(reason)
                    .message(message)
                    .restartCount(restarts)
                    .occurrences(1)
                    .recommendedSolution(RECOMMENDATION + " (" + reason + ")")
                    .sampleLogs(List.of())
                    .build());
        }
        return out;
    }
}
