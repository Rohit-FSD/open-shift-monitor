package com.yourorg.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ServiceLogStatus {
  private String service;
  private String healthStatus;
  private List<FailureDetail> failures;
  private List<PodFailureDetail> podFailures;
  private List<ApplicationErrorDetail> applicationErrors;
}

