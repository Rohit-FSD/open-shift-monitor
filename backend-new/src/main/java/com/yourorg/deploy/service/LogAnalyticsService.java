package com.yourorg.deploy.service;

import com.yourorg.deploy.dto.SuccessRateConfig;
import com.yourorg.deploy.dto.SuccessRateResponse;
import com.yourorg.deploy.model.FilterDefinition;
import com.yourorg.deploy.model.ParsedLogEntry;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the success-rate pipeline:
 *  1) fetch raw logs from OpenShift (via your existing JourneyLogService)
 *  2) parse lines into structured entries
 *  3) load requested filter definitions
 *  4) run aggregation and return the response
 *
 * NOTE: inject your existing JourneyLogService here. For clarity this file
 * references it via an interface — adjust the package/import to match your
 * project structure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogAnalyticsService {

    private final JourneyLogService journeyLogService;
    private final LogParserService logParserService;
    private final FilterDefinitionService filterDefinitionService;
    private final AggregationService aggregationService;

    public SuccessRateResponse calculateSuccessRate(SuccessRateConfig config) {
        String raw = fetchRawLogs(config);
        List<ParsedLogEntry> parsed = logParserService.parseLogLines(raw);
        log.info("Parsed {} log entries", parsed.size());

        Map<String, FilterDefinition> filters = loadFilters(config);
        log.info("Loaded {} filter definitions", filters.size());

        return aggregationService.calculateSuccessRate(parsed, filters, config);
    }

    private String fetchRawLogs(SuccessRateConfig config) {
        if (config.getServiceNames() != null && !config.getServiceNames().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String svc : config.getServiceNames()) {
                sb.append(journeyLogService.getServiceLogs(
                        config.getEnvName(), svc, config.getTimeRangeMinutes())).append("\n");
            }
            return sb.toString();
        }
        return journeyLogService.getAllLogsForEnvironment(
                config.getEnvName(), config.getTimeRangeMinutes());
    }

    private Map<String, FilterDefinition> loadFilters(SuccessRateConfig config) {
        Map<String, FilterDefinition> filters = new LinkedHashMap<>();
        addIfPresent(filters, config.getTotalAttemptsFilterId());
        if (config.getSuccessFilterIds() != null)
            config.getSuccessFilterIds().forEach(id -> addIfPresent(filters, id));
        if (config.getFailureFilterIds() != null)
            config.getFailureFilterIds().forEach(id -> addIfPresent(filters, id));
        return filters;
    }

    private void addIfPresent(Map<String, FilterDefinition> map, String id) {
        if (id == null) return;
        FilterDefinition f = filterDefinitionService.getFilterById(id);
        if (f != null) map.put(id, f);
        else log.warn("Filter not found: {}", id);
    }

    /** Port of your existing service - declare the methods you already have. */
    public interface JourneyLogService {
        String getAllLogsForEnvironment(String envName, int timeRangeMinutes);
        String getServiceLogs(String envName, String serviceName, int timeRangeMinutes);
    }
}
