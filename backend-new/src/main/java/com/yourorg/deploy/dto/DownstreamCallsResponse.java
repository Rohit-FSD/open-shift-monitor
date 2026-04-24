package com.yourorg.deploy.dto;

import com.yourorg.deploy.model.DownstreamApiCall;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DownstreamCallsResponse {
    private String searchId;
    private String environment;
    private String namespace;
    private int totalPodsSearched;
    private int totalCalls;
    private int successCount;
    private int errorCount;
    private List<DownstreamApiCall> calls;
    private LocalDateTime timestamp;
}
