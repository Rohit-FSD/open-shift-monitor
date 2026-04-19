package com.yourorg.deploy.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SuccessRateResponse {
    private SuccessRateConfig config;
    private Period period;
    private double overallSuccessRate;
    private long totalAttempts;
    private long successfulOperations;
    private long failedOperations;
    private List<FilterCategoryResult> categoryBreakdown;
    private List<TimeSeriesPoint> timeSeries;
    private List<Object> sampleLogs;
    private Map<String, Object> metadata;

    @Data
    @Builder
    public static class Period {
        private LocalDateTime start;
        private LocalDateTime end;
    }

    @Data
    @Builder
    public static class FilterCategoryResult {
        private String filterName;
        private String filterExpression;
        private String category;
        private long matchCount;
        private double percentage;
        private String color;
    }

    @Data
    @Builder
    public static class TimeSeriesPoint {
        private LocalDateTime timestamp;
        private long totalAttempts;
        private long successCount;
        private long failureCount;
        private double successRate;
    }
}
