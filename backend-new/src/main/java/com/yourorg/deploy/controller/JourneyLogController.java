package com.yourorg.deploy.controller;

import com.yourorg.deploy.dto.DownstreamCallsResponse;
import com.yourorg.deploy.service.JourneyLogService;
import com.yourorg.deploy.service.JourneyLogService.JourneyLogsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Downloads / searches pod logs by TID or Correlation ID across any environment.
 * Returns matching lines with 2-line context per pod.
 */
@Slf4j
@RestController
@RequestMapping("/api/journey-logs")
@RequiredArgsConstructor
@CrossOrigin(origins = {
        "http://localhost:3000", "http://localhost:4200",
        "http://localhost:8081", "http://localhost:5173"
})
public class JourneyLogController {

    private final JourneyLogService journeyLogService;

    // timeRangeMinutes is optional — omit it (or pass 0) to fetch ALL available logs
    @GetMapping("/search")
    public ResponseEntity<JourneyLogsResponse> search(
            @RequestParam String envName,
            @RequestParam String searchId,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) Integer timeRangeMinutes) {
        log.info("Journey search: env={} id={} service={} minutes={}",
                envName, searchId, serviceName, timeRangeMinutes != null ? timeRangeMinutes : "ALL");
        return ResponseEntity.ok(
                journeyLogService.getJourneyLogs(envName, searchId, serviceName, timeRangeMinutes));
    }

    /**
     * Extracts downstream HTTP API calls (request + response) for a given
     * correlationId / TID by parsing DEBUG-mode interceptor logs across all pods.
     *
     * GET /api/journey-logs/downstream-calls
     *   ?envName=SIT&searchId=c62b55a1-...&serviceName=bcp-css-service&timeRangeMinutes=60
     */
    @GetMapping("/downstream-calls")
    public ResponseEntity<DownstreamCallsResponse> downstreamCalls(
            @RequestParam String envName,
            @RequestParam String searchId,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) Integer timeRangeMinutes) {
        log.info("Downstream calls: env={} id={} service={} minutes={}",
                envName, searchId, serviceName, timeRangeMinutes != null ? timeRangeMinutes : "ALL");
        return ResponseEntity.ok(
                journeyLogService.getDownstreamCalls(envName, searchId, serviceName, timeRangeMinutes));
    }
}
