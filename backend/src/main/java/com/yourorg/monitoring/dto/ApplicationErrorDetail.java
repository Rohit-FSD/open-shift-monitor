package com.yourorg.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ApplicationErrorDetail {
  private String exceptionType;
  private String message;
  private List<String> sampleLogs;
}

