package com.yourorg.deploy.service;

import com.yourorg.deploy.dto.SuccessRateConfig;
import com.yourorg.deploy.dto.SuccessRateResponse;
import com.yourorg.deploy.dto.SuccessRateResponse.FilterCategoryResult;
import com.yourorg.deploy.dto.SuccessRateResponse.Period;
import com.yourorg.deploy.dto.SuccessRateResponse.TimeSeriesPoint;
import com.yourorg.deploy.model.FilterDefinition;
import com.yourorg.deploy.model.ParsedLogEntry;
import com.yourorg.deploy.service.FilterEngineService.FilterMatchResult;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Calculates success-rate metrics over a parsed log set using a set of filters.
 *
 * Core formula (same as backend image in docs):
 *   successRate = (uniqueLogsMatchingAnySuccessFilter / totalAttempts) * 100
 *
 * Unique-counting (Set) avoids double-counting when a log matches multiple filters.
 * totalAttempts = matchCount of the totalAttemptsFilter if provided, else
 *   fallback to unique(success) + unique(failure).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AggregationService {

    private final FilterEngineService filterEngineService;

    public SuccessRateResponse calculateSuccessRate(
            List<ParsedLogEntry> parsedLogs,
            Map<String, FilterDefinition> filters,
            SuccessRateConfig config) {

        long t0 = System.currentTimeMillis();
        Map<String, FilterMatchResult> filterResults = filterEngineService.applyFilters(parsedLogs, filters);

        // --- success: unique logs across any success filter
        Set<ParsedLogEntry> uniqueSuccess = unionMatches(filterResults, config.getSuccessFilterIds());
        Set<ParsedLogEntry> uniqueFailure = unionMatches(filterResults, config.getFailureFilterIds());

        long successCount = uniqueSuccess.size();
        long failureCount = uniqueFailure.size();

        // --- total attempts
        // Invariant we MUST enforce: attempts >= success + failure (actually attempts >= union(success, failure))
        // Previous bug: using only totalAttemptsFilter.matchCount could return a value smaller than
        // successCount when the attempts filter was narrower than the success filter (e.g. the
        // attempts filter had extra NOT_CONTAINS clauses), producing successRate > 100% or negative
        // "other" counts. Fix: take the UNION of the attempts filter matches with the success and
        // failure sets, so attempts is always a superset.
        Set<ParsedLogEntry> attemptsSet = new LinkedHashSet<>();
        if (config.getTotalAttemptsFilterId() != null
                && filterResults.containsKey(config.getTotalAttemptsFilterId())) {
            attemptsSet.addAll(filterResults.get(config.getTotalAttemptsFilterId()).getMatchingLogs());
        }
        attemptsSet.addAll(uniqueSuccess);
        attemptsSet.addAll(uniqueFailure);
        long totalAttempts = attemptsSet.size();

        double successRate = totalAttempts > 0
                ? Math.min(100.0, successCount * 100.0 / totalAttempts)
                : 0.0;

        // --- category breakdown
        List<FilterCategoryResult> breakdown = filterResults.values().stream()
                .map(r -> {
                    FilterDefinition fd = filters.get(r.getFilterId());
                    double pct = totalAttempts > 0 ? (r.getMatchCount() * 100.0 / totalAttempts) : 0.0;
                    return FilterCategoryResult.builder()
                            .filterName(r.getFilterName())
                            .filterExpression(fd == null || fd.getExpression() == null
                                    ? null : fd.getExpression().getOperator())
                            .category(r.getCategory() == null ? null : r.getCategory().name())
                            .matchCount(r.getMatchCount())
                            .percentage(pct)
                            .color(fd == null ? null : fd.getColor())
                            .build();
                })
                .sorted(Comparator.comparingLong(FilterCategoryResult::getMatchCount).reversed())
                .collect(Collectors.toList());

        // --- time series
        List<TimeSeriesPoint> timeSeries = buildTimeSeries(parsedLogs, uniqueSuccess, uniqueFailure, config);

        // --- sample logs
        List<Object> sampleLogs = Boolean.TRUE.equals(config.getIncludeSampleLogs())
                ? uniqueSuccess.stream().limit(5).map(l -> (Object) l.getRawLine()).collect(Collectors.toList())
                : List.of();

        // --- period
        Period period = parsedLogs.isEmpty() ? null : Period.builder()
                .start(parsedLogs.stream().map(ParsedLogEntry::getTimestamp).min(LocalDateTime::compareTo).orElse(null))
                .end(parsedLogs.stream().map(ParsedLogEntry::getTimestamp).max(LocalDateTime::compareTo).orElse(null))
                .build();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("executionTimeMs", System.currentTimeMillis() - t0);
        metadata.put("totalLogsScanned", parsedLogs.size());
        metadata.put("filterCount", filters.size());

        return SuccessRateResponse.builder()
                .config(config)
                .period(period)
                .overallSuccessRate(round(successRate))
                .totalAttempts(totalAttempts)
                .successfulOperations(successCount)
                .failedOperations(failureCount)
                .categoryBreakdown(breakdown)
                .timeSeries(timeSeries)
                .sampleLogs(sampleLogs)
                .metadata(metadata)
                .build();
    }

    private Set<ParsedLogEntry> unionMatches(Map<String, FilterMatchResult> results, List<String> ids) {
        Set<ParsedLogEntry> out = new LinkedHashSet<>();
        if (ids == null) return out;
        for (String id : ids) {
            FilterMatchResult r = results.get(id);
            if (r != null) out.addAll(r.getMatchingLogs());
        }
        return out;
    }

    private List<TimeSeriesPoint> buildTimeSeries(
            List<ParsedLogEntry> all,
            Set<ParsedLogEntry> success,
            Set<ParsedLogEntry> failure,
            SuccessRateConfig config) {
        if (all.isEmpty()) return List.of();
        ChronoUnit bucket = switch (config.getGroupBy() == null ? "HOUR" : config.getGroupBy().toUpperCase()) {
            case "MINUTE" -> ChronoUnit.MINUTES;
            case "DAY"    -> ChronoUnit.DAYS;
            case "WEEK"   -> ChronoUnit.WEEKS;
            case "MONTH"  -> ChronoUnit.MONTHS;
            default       -> ChronoUnit.HOURS;
        };
        Map<LocalDateTime, long[]> buckets = new TreeMap<>();
        for (ParsedLogEntry e : all) {
            LocalDateTime key = e.getTimestamp().truncatedTo(bucket);
            long[] slot = buckets.computeIfAbsent(key, k -> new long[3]);
            slot[0]++;
            if (success.contains(e)) slot[1]++;
            if (failure.contains(e)) slot[2]++;
        }
        return buckets.entrySet().stream()
                .map(e -> TimeSeriesPoint.builder()
                        .timestamp(e.getKey())
                        .totalAttempts(e.getValue()[0])
                        .successCount(e.getValue()[1])
                        .failureCount(e.getValue()[2])
                        .successRate(e.getValue()[0] > 0
                                ? round(e.getValue()[1] * 100.0 / e.getValue()[0]) : 0.0)
                        .build())
                .collect(Collectors.toList());
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
