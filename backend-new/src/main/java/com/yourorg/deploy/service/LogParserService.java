package com.yourorg.deploy.service;

import com.yourorg.deploy.model.ParsedLogEntry;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Parses log lines of the form:
 *  2026-04-17 10:10:38 555|[TID a210c5ce-...]|http-nio-8081-exec-5|INFO|[c62b55a1-...]|SessionFilter :: ...
 */
@Slf4j
@Service
public class LogParserService {

    private static final Pattern LOG_PATTERN = Pattern.compile(
            "(?<timestamp>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} \\d{3})\\|"
          + "\\[TID (?<tid>[^\\]]+)\\]\\|"
          + "(?<thread>[^|]+)\\|"
          + "(?<level>\\w+)\\|"
          + "\\[(?<correlationId>[^\\]]+)\\]\\|"
          + "(?<message>.+)");

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss SSS");

    // Optional patterns to extract derived fields inside the message
    private static final Pattern CUSTOMER_ID = Pattern.compile("customerId=(\\d+)");
    private static final Pattern ERROR_CODE  = Pattern.compile("(7[0-6]\\d)-?\\w*");

    public List<ParsedLogEntry> parseLogLines(String rawLogs) {
        if (rawLogs == null || rawLogs.isEmpty()) return List.of();
        List<ParsedLogEntry> out = new ArrayList<>();
        for (String line : rawLogs.split("\n")) {
            ParsedLogEntry entry = parseLine(line);
            if (entry != null) out.add(entry);
        }
        return out;
    }

    public ParsedLogEntry parseLine(String line) {
        if (line == null || line.trim().isEmpty()) return null;
        Matcher m = LOG_PATTERN.matcher(line);
        if (!m.matches()) return null;
        try {
            String msg = m.group("message");
            return ParsedLogEntry.builder()
                    .timestamp(LocalDateTime.parse(m.group("timestamp"), TS_FMT))
                    .tid(m.group("tid"))
                    .thread(m.group("thread"))
                    .logLevel(m.group("level"))
                    .correlationId(m.group("correlationId"))
                    .message(msg)
                    .rawLine(line)
                    .derivedFields(extractDerived(msg))
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse log line: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> extractDerived(String msg) {
        Map<String, String> out = new HashMap<>();
        Matcher cid = CUSTOMER_ID.matcher(msg);
        if (cid.find()) out.put("customerId", cid.group(1));
        Matcher ec = ERROR_CODE.matcher(msg);
        if (ec.find()) out.put("errorCode", ec.group(1));
        return out;
    }
}
