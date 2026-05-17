package com.yourorg.deploy.service;

import com.yourorg.deploy.config.OpenShiftProperties;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Resolves service-account tokens by logical account name.
 *
 * Tokens are read from application.yml under openshift.tokens.<accountName>.
 * In production these are mounted as secrets / vault-injected values.
 *
 * Also implements the JourneyLogService.TokenService interface so the same
 * bean satisfies both injection points.
 */
@Slf4j
@Service
@ConfigurationProperties(prefix = "openshift")
@RequiredArgsConstructor
public class TokenService implements JourneyLogService.TokenService {

    /** Bound from openshift.tokens.<accountName>: <token-value> */
    private Map<String, String> tokens = new ConcurrentHashMap<>();

    public void setTokens(Map<String, String> tokens) {
        this.tokens = tokens;
    }

    @Override
    public String getTokenValue(String systemAccount) {
        String token = tokens.get(systemAccount);
        if (token == null || token.isBlank()) {
            log.warn("No token configured for system-account '{}'. " +
                    "Add openshift.tokens.{}: <value> to application.yml", systemAccount, systemAccount);
            return "";
        }
        return token;
    }
}
