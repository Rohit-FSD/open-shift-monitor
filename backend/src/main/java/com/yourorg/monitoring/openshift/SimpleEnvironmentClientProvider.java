package com.yourorg.monitoring.openshift;

import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SimpleEnvironmentClientProvider implements EnvironmentClientProvider {
  private final KubernetesClient kubernetesClient;

  @Override
  public KubernetesClient getClient(String environment) {
    return kubernetesClient;
  }

  @Override
  public String getNamespace(String environment) {
    return environment;
  }
}

