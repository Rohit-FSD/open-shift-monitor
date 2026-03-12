package com.yourorg.monitoring.service.rules;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FailureRule {
  private String id;
  private FailureCategory category = FailureCategory.UNKNOWN;

  /**
   * Maps to your response `errorType` field. Keep this stable for the UI.
   * Examples: REST_TIMEOUT, SOAP_FAULT, DB_ERROR, NullPointerException, INTERNAL_SERVER_ERROR
   */
  private String errorType;

  /** Simple string-contains matches (case-sensitive by default, we lower-case at runtime). */
  private List<String> matchAny = new ArrayList<>();

  /** Regex matches (java.util.regex). */
  private List<String> regexAny = new ArrayList<>();

  private ExtractRules extract = new ExtractRules();

  @Data
  public static class ExtractRules {
    /** Optional: extract endpoint from block. */
    private String endpointRegex;
    /** Optional: extract HTTP method from block. */
    private String methodRegex;
    /** Optional: extract status code from block. */
    private String statusRegex;
    /** Optional: extract exception type from block. */
    private String exceptionRegex;
  }
}

