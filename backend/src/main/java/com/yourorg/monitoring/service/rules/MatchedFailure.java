package com.yourorg.monitoring.service.rules;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MatchedFailure {
  private String ruleId;
  private FailureCategory category;
  private String errorType;

  private String endpoint;
  private String httpMethod;
  private String statusCode;

  private String exceptionType;
  private String message;
}

