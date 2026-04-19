package com.yourorg.deploy.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogFailureDetectionResponse {

    private String envName;
    private String serviceName;
    private String healthStatus;
    private LocalDateTime analyzedAt;
    private int timeRangeMinutes;
    private Summary summary;
    private List<ApplicationErrorDetail> applicationErrors;
    private List<PodFailureDetail> podFailures;
    private List<DownstreamFailureDetail> downstreamFailures;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Summary {
        private long totalLogsScanned;
        private long totalFailures;
        private long applicationErrorCount;
        private long podFailureCount;
        private long downstreamFailureCount;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ApplicationErrorDetail {
        private String errorCode;
        private String errorMessage;
        private String endpoint;
        private long occurrences;
        private String severity;
        private String recommendedSolution;
        private String description;
        private List<String> steps;
        private List<String> sampleLogs;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PodFailureDetail {
        private String podName;
        private String reason;
        private String message;
        private int restartCount;
        private long occurrences;
        private String recommendedSolution;
        private List<String> sampleLogs;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DownstreamFailureDetail {
        private String dependency;
        private String protocol;
        private String sampleError;
        private long occurrences;
        private String recommendedSolution;
        private List<String> sampleLogs;
    }
}
