package com.yourorg.deploy.controller;

import com.yourorg.deploy.dto.LogFailureDetectionRequest;
import com.yourorg.deploy.dto.LogFailureDetectionResponse;
import com.yourorg.deploy.service.LogFailureDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/log-failure-detection")
@RequiredArgsConstructor
@CrossOrigin(origins = {
        "http://localhost:3000", "http://localhost:4200",
        "http://localhost:8081", "http://localhost:5173"
})
public class LogFailureDetectionController {

    private final LogFailureDetectionService service;

    @PostMapping("/analyze")
    public ResponseEntity<LogFailureDetectionResponse> analyze(@RequestBody LogFailureDetectionRequest req) {
        log.info("Log failure analysis: env={} service={} minutes={}",
                req.getEnvName(), req.getServiceName(), req.getTimeRangeMinutes());
        return ResponseEntity.ok(service.analyze(req));
    }
}
