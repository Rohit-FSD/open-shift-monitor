package com.yourorg.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FailureDetail {
  private String endpoint;
  private String statusCode;
  private String httpMethod;
  private String errorType;
  private String errorMessage;
  private List<String> sampleLogs;
}

