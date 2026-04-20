package com.yourorg.deploy.vault.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import lombok.Data;

/**
 * Shape returned by /v1/CSM/database2/static-creds/...
 * {
 *   "data": {
 *     "username": "...",
 *     "password": "...",
 *     "rotation_period": 86400,
 *     "ttl": 3600
 *   },
 *   "lease_duration": 3600,
 *   ...
 * }
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CsmSecretResponse {

    private Map<String, Object> data;

    private long leaseDuration;
}
