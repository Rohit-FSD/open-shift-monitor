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
    private String thread;
    private String correlationId;

    private LocalDateTime requestTimestamp;
    private String method;
    private String url;
    private String requestHeaders;
    private String requestBody;

    private LocalDateTime responseTimestamp;
    private Integer responseStatus;
    private String responseStatusText;
    private String responseHeaders;
    private String responseBody;

    /** Milliseconds between request and response; null if response not captured. */
    private Long durationMs;

    /**
     * SUCCESS     — 2xx response, OR SOAP response captured without FAULT,
     *               OR request captured with no explicit failure (INFO-only
     *               logs assume success; check {@code responseBody != null}
     *               to know whether the status is inferred or confirmed)
     * CLIENT_ERROR — 4xx response
     * SERVER_ERROR — 5xx response, OR SOAP response contained FAULT/FAILURE
     * TIMEOUT     — SocketTimeoutException / Read timed out pattern seen
     * CONN_ERROR  — Connection refused / ConnectException pattern seen
     */
    private String callStatus;
}
