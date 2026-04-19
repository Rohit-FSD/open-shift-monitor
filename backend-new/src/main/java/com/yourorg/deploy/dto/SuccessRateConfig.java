package com.yourorg.deploy.dto;

import java.util.List;
import lombok.Data;

@Data
public class SuccessRateConfig {
    private String name;
    private String envName;
    private Integer timeRangeMinutes;
    private List<String> serviceNames;
    private String totalAttemptsFilterId;
    private List<String> successFilterIds;
    private List<String> failureFilterIds;
    private Boolean includeSampleLogs;
    private String groupBy; // MINUTE | HOUR | DAY | WEEK | MONTH
}
