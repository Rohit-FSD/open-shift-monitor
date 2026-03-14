package com.yourorg.monitoring.service.rules;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class FailureRuleEngine {

  private final LogFailureProperties props;

  public Optional<MatchedFailure> match(String logBlock) {
    if (logBlock == null || logBlock.isBlank()) return Optional.empty();
    String lower = logBlock.toLowerCase(Locale.ROOT);

    for (FailureRule rule : props.getRules()) {
      if (matches(rule, lower, logBlock)) {
        return Optional.of(extract(rule, logBlock));
      }
    }
    return Optional.empty();
  }

  private boolean matches(FailureRule rule, String lowerText, String originalText) {
    for (String s : rule.getMatchAny()) {
      if (s == null || s.isBlank()) continue;
      if (lowerText.contains(s.toLowerCase(Locale.ROOT))) return true;
    }
    for (String r : rule.getRegexAny()) {
      if (r == null || r.isBlank()) continue;
      if (Pattern.compile(r, Pattern.CASE_INSENSITIVE).matcher(originalText).find()) return true;
    }
    return false;
  }

  private MatchedFailure extract(FailureRule rule, String text) {
    FailureRule.ExtractRules ex = rule.getExtract();

    String endpoint = firstMatch(ex.getEndpointRegex(), text);
    String method = firstMatch(ex.getMethodRegex(), text);
    String status = firstMatch(ex.getStatusRegex(), text);
    String exceptionType = firstMatch(ex.getExceptionRegex(), text);

    // #region agent log
    agentLog(
        "H1",
        "extract() result",
        "ruleId=\"" + safe(rule.getId()) + "\",endpoint=\"" + safe(endpoint) + "\",method=\"" + safe(method)
            + "\",status=\"" + safe(status) + "\",exceptionType=\"" + safe(exceptionType) + "\""
    );
    // #endregion

    return MatchedFailure.builder()
        .ruleId(rule.getId())
        .category(rule.getCategory())
        .errorType(rule.getErrorType() != null ? rule.getErrorType() : "UNKNOWN")
        .endpoint(endpoint)
        .httpMethod(method)
        .statusCode(status)
        .exceptionType(exceptionType)
        .message(text)
        .build();
  }

  private String firstMatch(String regex, String text) {
    if (regex == null || regex.isBlank() || text == null) return "";
    Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    Matcher m = p.matcher(text);
    if (!m.find()) return "";
    // Prefer group(1) when present, else whole match
    if (m.groupCount() >= 1 && m.group(1) != null) return m.group(1);
    return m.group();
  }

  private String safe(String s) {
    return s == null ? "" : s.replace("\"", "\\\"");
  }

  private void agentLog(String hypothesisId, String message, String dataFragment) {
    long ts = System.currentTimeMillis();
    String json =
        "{\"sessionId\":\"1d8923\",\"runId\":\"run1\",\"hypothesisId\":\""
            + hypothesisId
            + "\",\"location\":\"FailureRuleEngine.java:extract\",\"message\":\""
            + message.replace("\"", "\\\"")
            + "\",\"data\":{"
            + dataFragment
            + "},\"timestamp\":"
            + ts
            + "}\n";
    try (FileWriter fw = new FileWriter("/Users/rohit/Downloads/open-shift-monitor-ui/.cursor/debug-1d8923.log", true)) {
      fw.write(json);
    } catch (IOException ignored) {
    }
  }
}

