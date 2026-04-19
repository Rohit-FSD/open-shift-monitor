package com.yourorg.deploy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.deploy.model.ErrorCodeMapping;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Loads error-code → recommendation mappings from classpath JSON and
 * matches log messages against them.
 */
@Slf4j
@Service
public class ErrorCodeMappingService {

    private static final String RESOURCE = "error-codes/error-codes.json";

    private final Map<String, ErrorCodeMapping> mappingsByCode = new LinkedHashMap<>();
    private ErrorCodeMapping defaultMapping;

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            ErrorCodeMapping[] arr = new ObjectMapper().readValue(in, ErrorCodeMapping[].class);
            for (ErrorCodeMapping m : arr) {
                mappingsByCode.put(m.getCode(), m);
                if (ErrorCodeMapping.DEFAULT_CODE.equals(m.getCode())) defaultMapping = m;
            }
            log.info("Loaded {} error-code mappings", mappingsByCode.size());
        } catch (Exception e) {
            log.error("Failed to load {}: {}", RESOURCE, e.getMessage(), e);
        }
    }

    /** Find the first mapping whose regex pattern matches the message. */
    public Optional<ErrorCodeMapping> match(String message) {
        if (message == null) return Optional.empty();
        for (ErrorCodeMapping m : mappingsByCode.values()) {
            if (ErrorCodeMapping.DEFAULT_CODE.equals(m.getCode())) continue;
            if (m.compiled() == null) continue;
            Matcher matcher = m.compiled().matcher(message);
            if (matcher.find()) return Optional.of(m);
        }
        return Optional.empty();
    }

    public ErrorCodeMapping defaultMapping() { return defaultMapping; }
}
