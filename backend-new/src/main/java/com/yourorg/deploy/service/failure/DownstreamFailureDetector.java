package com.yourorg.deploy.service.failure;

import com.yourorg.deploy.dto.LogFailureDetectionResponse.DownstreamFailureDetail;
import com.yourorg.deploy.model.ParsedLogEntry;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DownstreamFailureDetector {

    private static final String RECOMMENDATION =
            "Downstream dependency failed — please contact the owning team";

    /** Pattern → protocol. Evaluated in order; first match wins for classification. */
    private static final LinkedHashMap<Pattern, String> SIGNALS = new LinkedHashMap<>();
    static {
        SIGNALS.put(Pattern.compile("SOAPFaultException|SOAPFault\\b", Pattern.CASE_INSENSITIVE), "SOAP");
        SIGNALS.put(Pattern.compile("(?:SQLException|SQLRecoverableException|JDBCConnectionException|DataAccessException|\\bORA-\\d{4,5}\\b)"), "DB");
        SIGNALS.put(Pattern.compile("(?:ResourceAccessException|RestClientException|HttpServerErrorException)"), "REST");
        SIGNALS.put(Pattern.compile("\\bstatus=5\\d\\d\\b"), "REST");
        SIGNALS.put(Pattern.compile("(?:Read timed out|Connection refused|SocketTimeoutException|ConnectException)",
                Pattern.CASE_INSENSITIVE), "NETWORK");
    }

    private static final Pattern HOST = Pattern.compile("https?://([^/\\s]+)");
    private static final Pattern SERVICE_TOKEN = Pattern.compile("\\b([a-z][a-z0-9-]{2,}-service)\\b");

    public List<DownstreamFailureDetail> detect(List<ParsedLogEntry> logs, boolean includeSamples, int sampleLimit) {
        // key = dependency|protocol
        Map<String, List<ParsedLogEntry>> grouped = new LinkedHashMap<>();
        Map<String, String[]> meta = new HashMap<>(); // key -> [dependency, protocol]

        for (ParsedLogEntry e : logs) {
            String msg = e.getMessage();
            if (msg == null) continue;
            String protocol = classify(msg);
            if (protocol == null) continue;
            String dep = extractDependency(msg);
            String key = dep + "|" + protocol;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
            meta.putIfAbsent(key, new String[]{dep, protocol});
        }

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<ParsedLogEntry> hits = entry.getValue();
                    String[] m = meta.get(entry.getKey());
                    return DownstreamFailureDetail.builder()
                            .dependency(m[0])
                            .protocol(m[1])
                            .sampleError(truncate(hits.get(0).getMessage(), 240))
                            .occurrences(hits.size())
                            .recommendedSolution(RECOMMENDATION)
                            .sampleLogs(includeSamples
                                    ? hits.stream().limit(sampleLimit).map(ParsedLogEntry::getRawLine).collect(Collectors.toList())
                                    : List.of())
                            .build();
                })
                .sorted(Comparator.comparingLong(DownstreamFailureDetail::getOccurrences).reversed())
                .collect(Collectors.toList());
    }

    private String classify(String msg) {
        for (Map.Entry<Pattern, String> e : SIGNALS.entrySet()) {
            if (e.getKey().matcher(msg).find()) return e.getValue();
        }
        return null;
    }

    private static final Pattern ORA_CODE = Pattern.compile("\\bORA-(\\d{4,5})\\b");

    private String extractDependency(String msg) {
        Matcher ora = ORA_CODE.matcher(msg);
        if (ora.find()) return "oracle-db (ORA-" + ora.group(1) + ")";
        Matcher h = HOST.matcher(msg);
        if (h.find()) return h.group(1);
        Matcher s = SERVICE_TOKEN.matcher(msg);
        if (s.find()) return s.group(1);
        return "unknown";
    }

    private String truncate(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max) + "...");
    }
}
