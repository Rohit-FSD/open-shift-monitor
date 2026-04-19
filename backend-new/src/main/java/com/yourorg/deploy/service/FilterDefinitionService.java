package com.yourorg.deploy.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.deploy.model.FilterDefinition;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilterDefinitionService {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final Map<String, FilterDefinition> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void load() {
        try {
            Resource resource = resourceLoader.getResource(
                    "classpath:filters/filter-definitions.json");
            if (!resource.exists()) {
                log.warn("filter-definitions.json not found, starting with empty cache");
                return;
            }
            try (InputStream in = resource.getInputStream()) {
                List<FilterDefinition> filters = objectMapper.readValue(
                        in, new TypeReference<List<FilterDefinition>>() {});
                filters.forEach(f -> cache.put(f.getId(), f));
                log.info("Loaded {} filter definitions", filters.size());
            }
        } catch (Exception e) {
            log.error("Failed to load filter definitions", e);
        }
    }

    public FilterDefinition getFilterById(String id) {
        return cache.get(id);
    }

    public List<FilterDefinition> list(boolean activeOnly, FilterDefinition.FilterCategory category) {
        return cache.values().stream()
                .filter(f -> !activeOnly || f.isActive())
                .filter(f -> category == null || f.getCategory() == category)
                .sorted(Comparator.comparingInt(FilterDefinition::getPriority).reversed())
                .collect(Collectors.toList());
    }
}
