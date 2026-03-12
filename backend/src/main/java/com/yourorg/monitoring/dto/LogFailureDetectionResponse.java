package com.yourorg.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LogFailureDetectionResponse {
  private String environment;
  private String timestamp;
  private List<ServiceLogStatus> services;
}

