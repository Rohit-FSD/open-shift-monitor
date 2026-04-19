package com.yourorg.deploy.controller;

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

    @GetMapping("/search")
    public ResponseEntity<JourneyLogsResponse> search(
            @RequestParam String envName,
            @RequestParam String searchId,
            @RequestParam(required = false) String serviceName,
            @RequestParam(defaultValue = "60") int timeRangeMinutes) {
        log.info("Journey search: env={} id={} service={} minutes={}",
                envName, searchId, serviceName, timeRangeMinutes);
        return ResponseEntity.ok(
                journeyLogService.getJourneyLogs(envName, searchId, serviceName, timeRangeMinutes));
    }
}
