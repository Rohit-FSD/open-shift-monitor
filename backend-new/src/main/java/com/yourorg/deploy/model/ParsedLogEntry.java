package com.yourorg.deploy.model;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParsedLogEntry {
    private LocalDateTime timestamp;
    private String tid;
    private String thread;
    private String logLevel;
    private String correlationId;
    private String message;
    private String rawLine;
    private Map<String, String> derivedFields;
}
