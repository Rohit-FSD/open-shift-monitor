package com.yourorg.deploy.vault;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the csm-vault.* block from application.yml.
 *
 * Example:
 *   csm-vault:
 *     enabled: true
 *     base-url: https://csm-uk1-stg.barcapint.com:8200
 *     role: apin156370_bcp_bbostest
 *     jwt-path: /var/run/secrets/tokens/vault-token
 *     login-path: /v1/CSM/auth/jwt/login
 *     db-secrets:
 *       cssqa: /v1/CSM/database2/static-creds/apin156370_cssqa_gcpcssdev
 */
@Data
@Component
@ConfigurationProperties(prefix = "csm-vault")
public class CsmVaultProperties {

    private boolean enabled = false;

    private String baseUrl;

    private String role;

    /** Projected SA token file on the pod. */
    private String jwtPath = "/var/run/secrets/tokens/vault-token";

    private String loginPath = "/v1/CSM/auth/jwt/login";

    /** Logical name -> full vault secret path (e.g. /v1/CSM/database2/static-creds/...). */
    private Map<String, String> dbSecrets = new HashMap<>();

    /** Optional dev-only JWT override. Ignored in prod. */
    private String devJwt;
}
