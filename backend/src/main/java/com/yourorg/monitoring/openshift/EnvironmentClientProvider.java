package com.yourorg.monitoring.openshift;

import io.fabric8.kubernetes.client.KubernetesClient;

public interface EnvironmentClientProvider {
  KubernetesClient getClient(String environment);

  /**
   * Map the UI environment to an OpenShift namespace.
   * If your environment equals namespace, just return environment.
   */
  String getNamespace(String environment);
}

