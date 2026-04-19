package com.yourorg.deploy.service;

import com.yourorg.deploy.dto.LogFailureDetectionRequest;
import com.yourorg.deploy.dto.LogFailureDetectionResponse;
import com.yourorg.deploy.dto.LogFailureDetectionResponse.ApplicationErrorDetail;
import com.yourorg.deploy.dto.LogFailureDetectionResponse.DownstreamFailureDetail;
import com.yourorg.deploy.dto.LogFailureDetectionResponse.PodFailureDetail;
import com.yourorg.deploy.dto.LogFailureDetectionResponse.Summary;
import com.yourorg.deploy.model.ParsedLogEntry;
import com.yourorg.deploy.service.JourneyLogService.ServiceSnapshot;
import com.yourorg.deploy.service.failure.ApplicationErrorDetector;
import com.yourorg.deploy.service.failure.DownstreamFailureDetector;
import com.yourorg.deploy.service.failure.PodFailureDetector;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates log-failure detection:
 *   1. Fetch pod statuses + raw logs for the service
 *   2. Classify pod-level failures from Kubernetes status
 *   3. Parse logs; classify application errors (mapped codes) and downstream failures
 *   4. Derive overall health status and return a structured response
 *
 * Pod failures take precedence over log-derived failures in terms of health downgrade.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogFailureDetectionService {

    private final JourneyLogService journeyLogService;
    private final LogParserService logParserService;
    private final PodFailureDetector podFailureDetector;
    private final ApplicationErrorDetector applicationErrorDetector;
    private final DownstreamFailureDetector downstreamFailureDetector;

    public LogFailureDetectionResponse analyze(LogFailureDetectionRequest req) {
        int minutes = req.resolvedTimeRangeMinutes();
        boolean includeSamples = req.resolvedIncludeSamples();
        int sampleLimit = req.resolvedSampleLimit();

        ServiceSnapshot snapshot = journeyLogService.getServiceSnapshot(
                req.getEnvName(), req.getServiceName(), minutes);

        List<PodFailureDetail> podFailures = podFailureDetector.detect(snapshot.getPods());

        List<ParsedLogEntry> parsed = logParserService.parseLogLines(snapshot.getRawLogs());
        List<ApplicationErrorDetail> applicationErrors =
                applicationErrorDetector.detect(parsed, includeSamples, sampleLimit);
        List<DownstreamFailureDetail> downstreamFailures =
                downstreamFailureDetector.detect(parsed, includeSamples, sampleLimit);

        long appCount = applicationErrors.stream().mapToLong(ApplicationErrorDetail::getOccurrences).sum();
        long podCount = podFailures.size();
        long downCount = downstreamFailures.stream().mapToLong(DownstreamFailureDetail::getOccurrences).sum();

        Summary summary = Summary.builder()
                .totalLogsScanned(parsed.size())
                .totalFailures(appCount + podCount + downCount)
                .applicationErrorCount(appCount)
                .podFailureCount(podCount)
                .downstreamFailureCount(downCount)
                .build();

        return LogFailureDetectionResponse.builder()
                .envName(req.getEnvName())
                .serviceName(req.getServiceName())
                .healthStatus(health(summary, parsed.size()))
                .analyzedAt(LocalDateTime.now())
                .timeRangeMinutes(minutes)
                .summary(summary)
                .applicationErrors(applicationErrors)
                .podFailures(podFailures)
                .downstreamFailures(downstreamFailures)
                .build();
    }

    private String health(Summary s, int logsScanned) {
        if (s.getPodFailureCount() > 0) return "DOWN";
        if (logsScanned == 0) return "DOWN";
        if (s.getTotalFailures() == 0) return "HEALTHY";
        return "DEGRADED";
    }
}
