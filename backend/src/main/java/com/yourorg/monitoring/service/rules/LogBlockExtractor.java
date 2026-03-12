package com.yourorg.monitoring.service.rules;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class LogBlockExtractor {

  public List<String> extractErrorBlocks(String tailLog, int maxBlocks, int maxBlockLines) {
    if (tailLog == null || tailLog.isBlank()) return List.of();

    String[] lines = tailLog.split("\n");
    List<String> blocks = new ArrayList<>();

    for (int i = 0; i < lines.length; i++) {
      if (!isErrorLike(lines[i])) continue;

      StringBuilder b = new StringBuilder();
      int end = Math.min(lines.length, i + maxBlockLines);
      for (int j = i; j < end; j++) {
        String line = lines[j];
        b.append(line).append("\n");
        // Stop early if we hit a clear separator
        if (line != null && line.trim().isEmpty() && j > i) break;
      }

      blocks.add(b.toString().trim());
      if (blocks.size() >= maxBlocks) break;
    }

    return blocks;
  }

  private boolean isErrorLike(String line) {
    if (line == null) return false;
    String s = line.toLowerCase(Locale.ROOT);
    return s.contains(" error ")
        || s.contains("error:")
        || s.contains("exception")
        || s.contains("failed")
        || s.contains("fault")
        || s.contains("timeout")
        || s.contains("connection refused")
        || s.contains("read timed out")
        || s.contains("internal server error")
        || s.contains("status=500")
        || s.contains("status code: 500");
  }
}

