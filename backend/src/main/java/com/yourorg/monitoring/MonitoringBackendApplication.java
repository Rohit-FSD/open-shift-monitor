package com.yourorg.monitoring;

import com.yourorg.monitoring.service.rules.LogFailureProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LogFailureProperties.class)
public class MonitoringBackendApplication {
  public static void main(String[] args) {
    SpringApplication.run(MonitoringBackendApplication.class, args);
  }
}

