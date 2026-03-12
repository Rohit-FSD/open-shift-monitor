package com.yourorg.monitoring.api;

import com.yourorg.monitoring.dto.LogFailureDetectionResponse;
import com.yourorg.monitoring.service.LogFailureDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/log-failure-detection")
public class LogFailureDetectionController {

  private final LogFailureDetectionService service;

  @GetMapping("/{environment}/{serviceName}")
  public ResponseEntity<LogFailureDetectionResponse> detect(
      @PathVariable String environment,
      @PathVariable String serviceName,
      @RequestParam(defaultValue = "2000") int tailLines
  ) {
    return ResponseEntity.ok(service.detect(environment, serviceName, tailLines));
  }
}

