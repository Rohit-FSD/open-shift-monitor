package com.yourorg.monitoring.service.rules;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "monitoring.log-failure")
public class LogFailureProperties {
  private int tailLines = 2000;
  private int maxSamplesPerContainer = 5;
  private int maxBlockLines = 25;
  private int maxLineLen = 800;
  private List<FailureRule> rules = new ArrayList<>();
}

