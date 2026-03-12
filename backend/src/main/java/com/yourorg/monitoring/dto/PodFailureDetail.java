package com.yourorg.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PodFailureDetail {
  private String podName;
  private String failureType;
  private String status;
  private String reason;
  private String message;
  private Integer restartCount;
  private String lastTransitionTime;
  private List<String> containerFailures;
}

