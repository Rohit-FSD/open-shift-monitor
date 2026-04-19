package com.yourorg.deploy.service;

import com.yourorg.deploy.model.FilterDefinition;
import com.yourorg.deploy.model.FilterDefinition.Condition;
import com.yourorg.deploy.model.FilterDefinition.Expression;
import com.yourorg.deploy.model.ParsedLogEntry;
import java.util.*;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Evaluates a FilterDefinition against a stream of parsed log lines and
 * returns the set of log entries that match. Uses a Set to naturally
 * deduplicate — this is why counting is consistent even when a single
 * log line matches multiple filters.
 */
@Slf4j
@Service
public class FilterEngineService {

    @Data
    public static class FilterMatchResult {
        private final String filterId;
        private final String filterName;
        private final FilterDefinition.FilterCategory category;
        private final Set<ParsedLogEntry> matchingLogs;

        public long getMatchCount() {
            return matchingLogs.size();
        }
    }

    public Map<String, FilterMatchResult> applyFilters(
            List<ParsedLogEntry> logs, Map<String, FilterDefinition> filters) {
        Map<String, FilterMatchResult> results = new LinkedHashMap<>();
        for (Map.Entry<String, FilterDefinition> e : filters.entrySet()) {
            FilterDefinition f = e.getValue();
            Set<ParsedLogEntry> matches = new LinkedHashSet<>();
            for (ParsedLogEntry log : logs) {
                if (evaluate(f.getExpression(), log)) {
                    matches.add(log);
                }
            }
            results.put(e.getKey(), new FilterMatchResult(f.getId(), f.getName(), f.getCategory(), matches));
        }
        return results;
    }

    public boolean evaluate(Expression expr, ParsedLogEntry log) {
        if (expr == null) return false;
        List<Boolean> condResults = new ArrayList<>();
        if (expr.getConditions() != null) {
            for (Condition c : expr.getConditions()) condResults.add(evaluateCondition(c, log));
        }
        if (expr.getSubExpressions() != null) {
            for (Expression sub : expr.getSubExpressions()) condResults.add(evaluate(sub, log));
        }
        if (condResults.isEmpty()) return false;

        String op = expr.getOperator() == null ? "AND" : expr.getOperator().toUpperCase();
        switch (op) {
            case "OR":  return condResults.contains(true);
            case "NOT": return !condResults.get(0);
            case "AND":
            default:    return !condResults.contains(false);
        }
    }

    private boolean evaluateCondition(Condition c, ParsedLogEntry log) {
        String fieldValue = extractField(c.getField(), log);
        if (fieldValue == null) return false;
        String target = c.getValue() == null ? "" : c.getValue();
        switch (c.getCondition().toUpperCase()) {
            case "CONTAINS":      return fieldValue.contains(target);
            case "NOT_CONTAINS":  return !fieldValue.contains(target);
            case "EQUALS":        return fieldValue.equals(target);
            case "STARTS_WITH":   return fieldValue.startsWith(target);
            case "ENDS_WITH":     return fieldValue.endsWith(target);
            case "REGEX":         return Pattern.compile(target).matcher(fieldValue).find();
            default:              return false;
        }
    }

    private String extractField(String field, ParsedLogEntry log) {
        if (field == null) return null;
        switch (field) {
            case "message":       return log.getMessage();
            case "level":         return log.getLogLevel();
            case "thread":        return log.getThread();
            case "tid":           return log.getTid();
            case "correlationId": return log.getCorrelationId();
            case "rawLine":       return log.getRawLine();
            default:
                return log.getDerivedFields() == null ? null : log.getDerivedFields().get(field);
        }
    }
}
