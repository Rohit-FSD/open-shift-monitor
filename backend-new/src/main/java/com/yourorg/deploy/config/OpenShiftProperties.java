package com.yourorg.deploy.config;

import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds openshift.environments.* from application.yml.
 *
 * Example YAML:
 *
 * openshift:
 *   environments:
 *     dev:
 *       namespace: bcp-dev
 *       cluster: ocp-dev
 *       realm: internal
 *       version: v4
 *       system-account: dev-sa
 *     prod:
 *       namespace: bcp-prod
 *       cluster: ocp-prod
 *       realm: external
 *       version: v4
 *       system-account: prod-sa
 */
@Data
@Component
@ConfigurationProperties(prefix = "openshift")
public class OpenShiftProperties {

    private Map<String, EnvDetails> environments;

    @Data
    public static class EnvDetails {
        /** OpenShift/Kubernetes namespace */
        private String namespace;
        /** Cluster identifier (e.g. "ocp-dev", "ocp-prod") */
        private String cluster;
        /** Realm identifier used to build the master URL (e.g. "internal", "external") */
        private String realm;
        /** API version segment (e.g. "v4") */
        private String version;
        /** Service-account name whose token is fetched via TokenService */
        private String systemAccount;
    }
}
