package com.yourorg.deploy.config;

import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Builds the OpenShift master URL from (version, cluster, realm).
 *
 * Example YAML:
 *
 * host-config:
 *   base-url-template: "https://api.{cluster}.{realm}.example.com:6443"
 *   # OR use an explicit map:
 *   hosts:
 *     v4-ocp-dev-internal:   https://api.ocp-dev.internal.example.com:6443
 *     v4-ocp-prod-external:  https://api.ocp-prod.external.example.com:6443
 */
@Data
@Component
@ConfigurationProperties(prefix = "host-config")
public class HostConfig {

    /**
     * Optional URL template; use {version}, {cluster}, {realm} placeholders.
     * Takes precedence over the hosts map if set.
     */
    private String baseUrlTemplate;

    /**
     * Explicit map keyed by "{version}-{cluster}-{realm}".
     * Fall back to template-based resolution when a key is absent.
     */
    private Map<String, String> hosts;

    /**
     * Returns the master URL for the given combination.
     * Resolution order: explicit map → template → sensible default.
     */
    public String getHost(String version, String cluster, String realm) {
        String key = version + "-" + cluster + "-" + realm;

        if (hosts != null && hosts.containsKey(key)) {
            return hosts.get(key);
        }

        if (baseUrlTemplate != null && !baseUrlTemplate.isBlank()) {
            return baseUrlTemplate
                    .replace("{version}", version != null ? version : "")
                    .replace("{cluster}", cluster != null ? cluster : "")
                    .replace("{realm}", realm != null ? realm : "");
        }

        // Fallback: construct a conventional OpenShift API URL
        return "https://api." + cluster + "." + realm + ".example.com:6443";
    }
}
