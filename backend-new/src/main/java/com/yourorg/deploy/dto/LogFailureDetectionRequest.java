package com.yourorg.deploy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogFailureDetectionRequest {
    private String envName;
    private String serviceName;
    private Integer timeRangeMinutes;
    private Boolean includeSamples;
    private Integer sampleLimit;

    public int resolvedTimeRangeMinutes() { return timeRangeMinutes == null ? 30 : timeRangeMinutes; }
    public boolean resolvedIncludeSamples() { return includeSamples == null || includeSamples; }
    public int resolvedSampleLimit() { return sampleLimit == null ? 5 : sampleLimit; }
}
