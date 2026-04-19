package com.yourorg.deploy.controller;

import com.yourorg.deploy.dto.SuccessRateConfig;
import com.yourorg.deploy.dto.SuccessRateResponse;
import com.yourorg.deploy.model.FilterDefinition;
import com.yourorg.deploy.service.FilterDefinitionService;
import com.yourorg.deploy.service.LogAnalyticsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Minimal controller for success-rate + filter listing.
 * The old GET /success-rate/{envName} shortcut and write endpoints on /filters
 * have been removed - the UI only needs list + calculate.
 */
@Slf4j
@RestController
@RequestMapping("/api/deployments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DeploymentController {

    private final LogAnalyticsService logAnalyticsService;
    private final FilterDefinitionService filterDefinitionService;

    @PostMapping("/success-rate/calculate")
    public ResponseEntity<SuccessRateResponse> calculateSuccessRate(
            @RequestBody SuccessRateConfig config) {
        log.info("Success rate calculation request: {}", config.getName());
        try {
            return ResponseEntity.ok(logAnalyticsService.calculateSuccessRate(config));
        } catch (Exception e) {
            log.error("Error calculating success rate: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/filters")
    public ResponseEntity<List<FilterDefinition>> listFilters(
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly,
            @RequestParam(required = false) FilterDefinition.FilterCategory category) {
        return ResponseEntity.ok(filterDefinitionService.list(activeOnly, category));
    }

    @GetMapping("/filters/{id}")
    public ResponseEntity<FilterDefinition> getFilter(@PathVariable String id) {
        FilterDefinition f = filterDefinitionService.getFilterById(id);
        return f == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(f);
    }
}
