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
 * Parses OpenShift pod logs. Supports multiple real-world formats:
 *
 *   A) Pipe-delimited app format with TID + correlationId:
 *      2026-04-17 10:10:38 555|[TID a210c5ce-...]|http-nio-8081-exec-5|INFO|[c62b55a1-...]|SessionFilter :: ...
 *
 *   B) Pipe-delimited app format with EMPTY brackets:
 *      2026-04-18 10:34:50 260|[]|scheduling-1|INFO|[]|CSMUtility.fetchLatestDBPassFromCSM , Start
 *
 *   C) java.util.logging format (two-line, or JBoss/WebLogic):
 *      Apr 18, 2026 12:03:26 PM oracle.jdbc.driver.PhysicalConnection connect
 *      INFO: I:thread-1511 HikariPool-1:connection-adder entering args ...
 *
 *   D) Stack trace / unstructured lines (ORA-17820, "at oracle.jdbc..."):
 *      these are emitted as entries with the raw line as the message, so
 *      classifiers (downstream/application) can still match patterns.
 *
 * Empty / blank lines are skipped. Nothing is dropped silently — if a line has
 * content but doesn't match a known format, it becomes a fallback entry.
 */
@Slf4j
@Service
public class LogParserService {

    /** Format A/B: pipe-delimited app logs. [TID ...] or [] both accepted; correlationId may be empty. */
    private static final Pattern APP_LOG_PATTERN = Pattern.compile(
            "(?<timestamp>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} \\d{3})\\|"
          + "\\[(?:TID )?(?<tid>[^\\]]*)\\]\\|"
          + "(?<thread>[^|]+)\\|"
          + "(?<level>\\w+)\\|"
          + "\\[(?<correlationId>[^\\]]*)\\]\\|"
          + "(?<message>.+)");

    /** Format C: java.util.logging header line e.g. "Apr 18, 2026 12:03:26 PM ..." */
    private static final Pattern JUL_HEADER = Pattern.compile(
            "(?<month>\\w{3}) (?<day>\\d{1,2}), (?<year>\\d{4}) "
          + "(?<hour>\\d{1,2}):(?<min>\\d{2}):(?<sec>\\d{2}) (?<ampm>AM|PM) "
          + "(?<logger>[^\\s]+)\\s+(?<message>.*)");

    /** Format C second line: "INFO: message" */
    private static final Pattern JUL_BODY = Pattern.compile(
            "(?<level>INFO|WARN|WARNING|ERROR|SEVERE|FINE|DEBUG|FATAL):\\s+(?<message>.+)");

    private static final DateTimeFormatter TS_FMT_APP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss SSS");
    private static final DateTimeFormatter TS_FMT_JUL =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm:ss a", Locale.ENGLISH);

    private static final Pattern CUSTOMER_ID = Pattern.compile("customerId=(\\d+)");
    private static final Pattern ERROR_CODE  = Pattern.compile("(7[0-6]\\d)-?\\w*");

    public List<ParsedLogEntry> parseLogLines(String rawLogs) {
        if (rawLogs == null || rawLogs.isEmpty()) return List.of();
        List<ParsedLogEntry> out = new ArrayList<>();
        String[] lines = rawLogs.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.trim().isEmpty()) continue;

            // A/B: pipe-delimited app log
            ParsedLogEntry entry = tryAppLog(line);
            if (entry != null) { out.add(entry); continue; }

            // C: java.util.logging header — try to pair with next line as body
            ParsedLogEntry jul = tryJul(line, (i + 1 < lines.length) ? lines[i + 1] : null);
            if (jul != null) { out.add(jul); continue; }

            // D: fallback — raw line so classifiers can still match ORA-, "at ...", etc.
            out.add(ParsedLogEntry.builder()
                    .timestamp(LocalDateTime.now())
                    .logLevel(guessLevel(line))
                    .message(line)
                    .rawLine(line)
                    .derivedFields(new HashMap<>())
                    .build());
        }
        log.info("Parsed {} entries from {} raw lines", out.size(), lines.length);
        return out;
    }

    public ParsedLogEntry parseLine(String line) {
        if (line == null || line.trim().isEmpty()) return null;
        ParsedLogEntry e = tryAppLog(line);
        return e != null ? e : tryJul(line, null);
    }

    private ParsedLogEntry tryAppLog(String line) {
        Matcher m = APP_LOG_PATTERN.matcher(line);
        if (!m.matches()) return null;
        try {
            String msg = m.group("message");
            return ParsedLogEntry.builder()
                    .timestamp(LocalDateTime.parse(m.group("timestamp"), TS_FMT_APP))
                    .tid(emptyToNull(m.group("tid")))
                    .thread(m.group("thread"))
                    .logLevel(m.group("level"))
                    .correlationId(emptyToNull(m.group("correlationId")))
                    .message(msg)
                    .rawLine(line)
                    .derivedFields(extractDerived(msg))
                    .build();
        } catch (Exception e) {
            log.debug("tryAppLog failed: {}", e.getMessage());
            return null;
        }
    }

    private ParsedLogEntry tryJul(String headerLine, String maybeBody) {
        Matcher h = JUL_HEADER.matcher(headerLine);
        if (!h.matches()) return null;
        try {
            String ts = h.group("month") + " " + h.group("day") + ", " + h.group("year") + " "
                    + h.group("hour") + ":" + h.group("min") + ":" + h.group("sec") + " " + h.group("ampm");
            LocalDateTime t = LocalDateTime.parse(ts, TS_FMT_JUL);

            String level = "INFO";
            String message = h.group("logger") + " " + h.group("message");
            if (maybeBody != null) {
                Matcher b = JUL_BODY.matcher(maybeBody.trim());
                if (b.matches()) {
                    level = normalizeLevel(b.group("level"));
                    message = message + " :: " + b.group("message");
                }
            }

            return ParsedLogEntry.builder()
                    .timestamp(t)
                    .logLevel(level)
                    .message(message)
                    .rawLine(headerLine + (maybeBody != null ? "\n" + maybeBody : ""))
                    .derivedFields(extractDerived(message))
                    .build();
        } catch (Exception e) {
            log.debug("tryJul failed: {}", e.getMessage());
            return null;
        }
    }

    private String normalizeLevel(String lvl) {
        if (lvl == null) return "INFO";
        return switch (lvl.toUpperCase()) {
            case "SEVERE", "FATAL" -> "ERROR";
            case "WARNING" -> "WARN";
            default -> lvl.toUpperCase();
        };
    }

    private String guessLevel(String line) {
        String u = line.toUpperCase();
        if (u.contains("EXCEPTION") || u.contains("ERROR") || u.startsWith("AT ")) return "ERROR";
        if (u.contains("WARN")) return "WARN";
        return "INFO";
    }

    private String emptyToNull(String s) { return (s == null || s.isEmpty()) ? null : s; }

    private Map<String, String> extractDerived(String msg) {
        Map<String, String> out = new HashMap<>();
        if (msg == null) return out;
        Matcher cid = CUSTOMER_ID.matcher(msg);
        if (cid.find()) out.put("customerId", cid.group(1));
        Matcher ec = ERROR_CODE.matcher(msg);
        if (ec.find()) out.put("errorCode", ec.group(1));
        return out;
    }
}
