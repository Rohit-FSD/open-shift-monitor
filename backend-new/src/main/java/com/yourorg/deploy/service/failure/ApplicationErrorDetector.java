package com.yourorg.deploy.service.failure;

import com.yourorg.deploy.dto.LogFailureDetectionResponse.ApplicationErrorDetail;
import com.yourorg.deploy.model.ErrorCodeMapping;
import com.yourorg.deploy.model.ParsedLogEntry;
import com.yourorg.deploy.service.ErrorCodeMappingService;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationErrorDetector {

    private static final Pattern ENDPOINT = Pattern.compile("endpoint=([^\\s,]+)|uri=([^\\s,]+)|POST\\s+([^\\s]+)|GET\\s+([^\\s]+)");

    private final ErrorCodeMappingService mappings;

    public List<ApplicationErrorDetail> detect(List<ParsedLogEntry> logs, boolean includeSamples, int sampleLimit) {
        // Group by error-code
        Map<String, List<ParsedLogEntry>> grouped = new LinkedHashMap<>();
        Map<String, ErrorCodeMapping> mappingByCode = new HashMap<>();

        for (ParsedLogEntry e : logs) {
            if (!isErrorLevel(e.getLogLevel())) continue;
            mappings.match(e.getMessage()).ifPresent(m -> {
                grouped.computeIfAbsent(m.getCode(), k -> new ArrayList<>()).add(e);
                mappingByCode.putIfAbsent(m.getCode(), m);
            });
        }

        return grouped.entrySet().stream()
                .map(entry -> {
                    String code = entry.getKey();
                    List<ParsedLogEntry> hits = entry.getValue();
                    ErrorCodeMapping m = mappingByCode.get(code);
                    ParsedLogEntry first = hits.get(0);
                    return ApplicationErrorDetail.builder()
                            .errorCode(code)
                            .errorMessage(first.getMessage())
                            .endpoint(extractEndpoint(first.getMessage()))
                            .occurrences(hits.size())
                            .severity(m.getSeverity())
                            .recommendedSolution(m.getRecommendedSolution())
                            .description(m.getDescription())
                            .steps(m.getSteps())
                            .sampleLogs(includeSamples
                                    ? hits.stream().limit(sampleLimit).map(ParsedLogEntry::getRawLine).collect(Collectors.toList())
                                    : List.of())
                            .build();
                })
                .sorted(Comparator.comparingLong(ApplicationErrorDetail::getOccurrences).reversed())
                .collect(Collectors.toList());
    }

    private boolean isErrorLevel(String level) {
        return level != null && (level.equalsIgnoreCase("ERROR") || level.equalsIgnoreCase("WARN") || level.equalsIgnoreCase("FATAL"));
    }

    private String extractEndpoint(String msg) {
        if (msg == null) return null;
        Matcher m = ENDPOINT.matcher(msg);
        if (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                if (m.group(i) != null) return m.group(i);
            }
        }
        return null;
    }
}
