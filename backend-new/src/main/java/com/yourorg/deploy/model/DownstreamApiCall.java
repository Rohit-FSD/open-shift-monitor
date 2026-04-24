package com.yourorg.deploy.model;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class DownstreamApiCall {
    private String id;
    private String serviceName;
    private String podName;
    private String correlationId;

    private LocalDateTime requestTimestamp;
    private String method;
    private String url;
    private String requestBody;
    private String responseBody;

    /**
     * SUCCESS      — INFO-only request captured with no explicit failure, OR
     *                SOAP response captured without FAULT/FAILED, OR 2xx REST response
     * CLIENT_ERROR — 4xx response
     * SERVER_ERROR — 5xx response, OR SOAP response contained FAULT/FAILURE
     * TIMEOUT      — SocketTimeoutException / Read timed out pattern seen
     * CONN_ERROR   — Connection refused / ConnectException pattern seen
     */
    private String callStatus;
}
