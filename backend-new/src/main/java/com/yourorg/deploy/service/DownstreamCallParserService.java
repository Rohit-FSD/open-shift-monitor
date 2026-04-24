package com.yourorg.deploy.service;

import com.yourorg.deploy.model.DownstreamApiCall;
import com.yourorg.deploy.model.ParsedLogEntry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Extracts structured downstream HTTP/SOAP call records from raw pod logs.
 *
 * Actual log format observed (pipe-delimited, double-pipe before message):
 *   2026-04-18 09:51:53 103|[TID xxx]|http-nio-8081-exec-7|DEBUG|[corrId]||message
 *
 * Two call styles are supported:
 *
 * ── SOAP style (primary) ──────────────────────────────────────────────────────
 *   Request context (BEM header builder lines appear before the client call):
 *     BEMRequestBuilder :: buildBEMRequestHeader :: Selected header build strategy: "STANDARD"
 *     BEMRequestBuilder :: addOsOverrideDetails :: Override Values are [Environment, QAOSE, ODS]
 *
 *   Call start (triggers a new downstream-call record):
 *     StatementAccountReportingClient :: getIndividualCustomerStatementDetails ::
 *
 *   Response (closes the current record):
 *     Response for RetrieveIndividualCustomerStatementDetailsResponse in XML format : <?xml ...>
 *
 * ── REST style (fallback for HTTP client debug logs) ─────────────────────────
 *   POST http://downstream-service/api/v1/endpoint
 *   Request body: {...}
 *   Response Status: 200 OK
 *   Response body: {...}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DownstreamCallParserService {

    private final LogParserService logParserService;

    // -------------------------------------------------------------------------
    // SOAP patterns  (must be checked in order — BEM_DETAIL before SOAP_START)
    // -------------------------------------------------------------------------

    /**
     * BEM (Barclays Enterprise Message) request header builder lines.
     * These appear BEFORE the actual client call and are buffered as request context.
     *   BEMRequestBuilder :: buildBEMRequestHeader :: ...
     *   BEMRequestBuilder :: addOsOverrideDetails :: ...
     * Must be matched BEFORE SOAP_START to prevent double-matching.
     */
    private static final Pattern BEM_DETAIL = Pattern.compile(
            "(?i)BEMRequestBuilder\\s*::\\s*(.+)"
    );

    /**
     * SOAP client method invocation — triggers a new downstream-call record.
     *   StatementAccountReportingClient :: getIndividualCustomerStatementDetails ::
     *   SomeServiceAdapter :: fetchData
     */
    private static final Pattern SOAP_START = Pattern.compile(
            "(?i)(\\w+(?:Client|Reporter|Service|Adapter|Gateway|Handler|Caller))\\s*::\\s*(\\w+)"
    );

    /**
     * SOAP/XML request body — either on its own line, or tail of the SOAP_START line.
     *   Request for RetrieveIndividualCustRelationshipListRequest in XML format : <?xml ...>
     *   ClientName :: method :: Request for X in XML format : <?xml ...>  (single-line combined)
     */
    private static final Pattern SOAP_REQUEST_XML = Pattern.compile(
            "(?i)request\\s+for\\s+(\\w+)\\s+in\\s+(?:xml|json|soap)\\s+format\\s*:\\s*(.+)"
    );

    /**
     * SOAP/XML response — closes the current call record.
     *   Response for RetrieveIndividualCustomerStatementDetailsResponse in XML format : <?xml ...>
     *   ClientName :: method :: Response for X in XML format : <?xml ...>  (single-line combined)
     */
    private static final Pattern SOAP_RESPONSE = Pattern.compile(
            "(?i)response\\s+for\\s+(\\w+)\\s+in\\s+(?:xml|json|soap)\\s+format\\s*:\\s*(.+)"
    );

    // -------------------------------------------------------------------------
    // REST patterns  (fallback)
    // -------------------------------------------------------------------------

    /** Inline method + URL: "POST http://...", ">>> GET http://...", "HTTP POST http://..." */
    private static final Pattern REQ_INLINE = Pattern.compile(
            "(?i)(?:>>>\\s*|calling\\s*:?\\s*|outbound\\s+|http\\s+)?" +
            "(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+(https?://\\S+)"
    );

    /** Split pattern — URI on its own line: "URI: http://..." */
    private static final Pattern REQ_URI_ONLY = Pattern.compile(
            "(?i)(?:request\\s+)?(?:url|uri)\\s*[=:]\\s*(https?://\\S+)"
    );

    /** Split pattern — Method on its own line (paired with pending URI). */
    private static final Pattern REQ_METHOD_ONLY = Pattern.compile(
            "(?i)method\\s*[=:]\\s*(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)"
    );

    /** Request headers line. */
    private static final Pattern REQ_HEADERS = Pattern.compile(
            "(?i)request\\s+headers?\\s*[=:]\\s*(.+)"
    );

    /** Request body line. */
    private static final Pattern REQ_BODY = Pattern.compile(
            "(?i)(?:request|req)\\s+body\\s*[=:]\\s*(.+)"
    );

    /** HTTP response status — various interceptor formats. */
    private static final Pattern RESP_STATUS = Pattern.compile(
            "(?i)(?:response\\s+status\\s*[=:]?\\s*|resp(?:onse)?\\s+|<<<\\s*|status\\s+code\\s*[=:]?\\s*|status[=:]\\s*)(\\d{3})(?:\\s+(.*))?"
    );

    /** Spring RestTemplate built-in: "Response 200 OK". */
    private static final Pattern RESP_SPRING = Pattern.compile(
            "(?i)response\\s+(\\d{3})\\s+(\\S.*)"
    );

    /** Response headers line. */
    private static final Pattern RESP_HEADERS = Pattern.compile(
            "(?i)response\\s+headers?\\s*[=:]\\s*(.+)"
    );

    /** Response body line. */
    private static final Pattern RESP_BODY = Pattern.compile(
            "(?i)(?:response|resp)\\s+body\\s*[=:]\\s*(.+)"
    );

    /** Network-level errors (appear at ERROR/WARN level but belong to the same journey). */
    private static final Pattern TIMEOUT = Pattern.compile(
            "(?i)SocketTimeoutException|Read timed out|connection timed out|connect timeout"
    );
    private static final Pattern CONN_ERROR = Pattern.compile(
            "(?i)Connection refused|ConnectException|No route to host"
    );

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public List<DownstreamApiCall> parseDownstreamCalls(
            String rawLogs, String searchId, String podName) {

        if (rawLogs == null || rawLogs.isEmpty()) return List.of();

        List<ParsedLogEntry> allEntries = logParserService.parseLogLines(rawLogs);

        List<ParsedLogEntry> relevant = allEntries.stream()
                .filter(e -> matchesSearchId(e, searchId))
                .collect(Collectors.toList());

        if (relevant.isEmpty()) return List.of();

        log.debug("Found {} relevant entries for searchId={} pod={}", relevant.size(), searchId, podName);

        Map<String, List<ParsedLogEntry>> byThread = relevant.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getThread() != null ? e.getThread() : "unknown",
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        String serviceName = extractServiceName(podName);
        List<DownstreamApiCall> calls = new ArrayList<>();

        for (Map.Entry<String, List<ParsedLogEntry>> threadGroup : byThread.entrySet()) {
            List<ParsedLogEntry> entries = threadGroup.getValue();
            entries.sort(Comparator.comparing(ParsedLogEntry::getTimestamp,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            calls.addAll(extractCallsFromThread(entries, threadGroup.getKey(), podName, serviceName, searchId));
        }

        log.info("Extracted {} downstream calls for searchId={} pod={}", calls.size(), searchId, podName);
        return calls;
    }

    // -------------------------------------------------------------------------
    // State machine — one run per thread group
    // -------------------------------------------------------------------------

    private List<DownstreamApiCall> extractCallsFromThread(
            List<ParsedLogEntry> entries, String thread,
            String podName, String serviceName, String searchId) {

        List<DownstreamApiCall> result = new ArrayList<>();
        CallState state = null;
        String pendingUri = null;
        // BEM header lines appear before the SOAP client call — buffer them as request context
        List<String> bemBuffer = new ArrayList<>();

        for (ParsedLogEntry e : entries) {
            String msg = cleanMessage(e.getMessage());
            if (msg == null || msg.isBlank()) continue;

            // ── 1. BEM request context (buffer, not a call start) ──────────────
            Matcher bemMatch = BEM_DETAIL.matcher(msg);
            if (bemMatch.find()) {
                bemBuffer.add(bemMatch.group(1).trim());
                continue;
            }

            // ── 2. SOAP client method invocation (call start) ──────────────────
            Matcher soapMatch = SOAP_START.matcher(msg);
            if (soapMatch.find()) {
                if (state != null) result.add(state.build());
                String endpoint = "soap:" + soapMatch.group(1) + "::" + soapMatch.group(2);
                state = new CallState(podName, serviceName, thread, searchId,
                        e.getTimestamp(), "SOAP", endpoint);
                if (!bemBuffer.isEmpty()) {
                    state.requestHeaders = String.join("\n", bemBuffer);
                }
                bemBuffer.clear();

                // Same line may also carry "Request for X in XML format : <body>"
                // (single-line pattern: ClientName :: method :: Request for X ...)
                Matcher inlineReq = SOAP_REQUEST_XML.matcher(msg);
                if (inlineReq.find()) {
                    state.requestBody = inlineReq.group(2);
                }

                // Same line may also carry "Response for X in XML format : <body>"
                // (single-line pattern: ClientName :: method :: Response for X ...)
                Matcher inlineResp = SOAP_RESPONSE.matcher(msg);
                if (inlineResp.find()) {
                    state.responseBody = inlineResp.group(2);
                    state.responseStatusText = inlineResp.group(1);
                    state.responseTimestamp = e.getTimestamp();
                    state.inResponse = true;
                    state.forcedStatus = deriveSoapStatus(inlineResp.group(2));
                    result.add(state.build());
                    state = null;
                }
                continue;
            }

            // ── 2b. Standalone "Request for X in XML format : <body>" line ─────
            //    (for the two-line pattern where request XML is on its own line)
            if (state != null && !state.inResponse) {
                Matcher reqXml = SOAP_REQUEST_XML.matcher(msg);
                if (reqXml.find()) {
                    state.requestBody = reqXml.group(2);
                    continue;
                }
            }

            // ── 3. REST inline method + URL (call start) ───────────────────────
            Matcher inline = REQ_INLINE.matcher(msg);
            if (inline.find()) {
                if (state != null) result.add(state.build());
                bemBuffer.clear();
                state = new CallState(podName, serviceName, thread, searchId,
                        e.getTimestamp(), inline.group(1).toUpperCase(), inline.group(2));
                pendingUri = null;
                continue;
            }

            // ── 4. REST split URI ──────────────────────────────────────────────
            Matcher uriOnly = REQ_URI_ONLY.matcher(msg);
            if (uriOnly.find()) {
                pendingUri = uriOnly.group(1);
                continue;
            }

            // ── 5. REST split Method (pairs with pending URI) ──────────────────
            Matcher methodOnly = REQ_METHOD_ONLY.matcher(msg);
            if (methodOnly.find() && pendingUri != null) {
                if (state != null) result.add(state.build());
                bemBuffer.clear();
                state = new CallState(podName, serviceName, thread, searchId,
                        e.getTimestamp(), methodOnly.group(1).toUpperCase(), pendingUri);
                pendingUri = null;
                continue;
            }

            // No active call — skip detail lines
            if (state == null) continue;

            // ── 6. SOAP response (closes the current call) ─────────────────────
            Matcher soapResp = SOAP_RESPONSE.matcher(msg);
            if (soapResp.find()) {
                state.responseBody = soapResp.group(2);
                state.responseStatusText = soapResp.group(1);  // e.g. "RetrieveIndividualCustomerStatementDetailsResponse"
                state.responseTimestamp = e.getTimestamp();
                state.inResponse = true;
                state.forcedStatus = deriveSoapStatus(soapResp.group(2));
                result.add(state.build());
                state = null;
                continue;
            }

            // ── 7. REST response status ────────────────────────────────────────
            Matcher respSt = RESP_STATUS.matcher(msg);
            if (respSt.find()) {
                state.responseStatus = Integer.parseInt(respSt.group(1));
                state.responseStatusText = respSt.group(2) != null ? respSt.group(2).trim() : "";
                state.responseTimestamp = e.getTimestamp();
                state.inResponse = true;
                continue;
            }
            Matcher respSp = RESP_SPRING.matcher(msg);
            if (respSp.find()) {
                state.responseStatus = Integer.parseInt(respSp.group(1));
                state.responseStatusText = respSp.group(2).trim();
                state.responseTimestamp = e.getTimestamp();
                state.inResponse = true;
                continue;
            }

            // ── 8. Response body ───────────────────────────────────────────────
            Matcher respBody = RESP_BODY.matcher(msg);
            if (respBody.find()) {
                state.responseBody = respBody.group(1);
                continue;
            }

            // ── 9. Response headers ────────────────────────────────────────────
            Matcher respHdr = RESP_HEADERS.matcher(msg);
            if (respHdr.find()) {
                state.responseHeaders = respHdr.group(1);
                continue;
            }

            // ── 10. Request body / headers (only before response phase) ────────
            if (!state.inResponse) {
                Matcher reqBody = REQ_BODY.matcher(msg);
                if (reqBody.find()) { state.requestBody = reqBody.group(1); continue; }

                Matcher reqHdr = REQ_HEADERS.matcher(msg);
                if (reqHdr.find()) { state.requestHeaders = reqHdr.group(1); continue; }
            }

            // ── 11. Network errors ─────────────────────────────────────────────
            if (TIMEOUT.matcher(msg).find()) {
                state.forcedStatus = "TIMEOUT";
                result.add(state.build());
                state = null;
            } else if (CONN_ERROR.matcher(msg).find()) {
                state.forcedStatus = "CONN_ERROR";
                result.add(state.build());
                state = null;
            }
        }

        if (state != null) result.add(state.build());
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * The pipe-delimited log format uses a double-pipe before the message:
     *   ...|[corrId]||actual message
     * so the parsed message field starts with "|". Strip all leading pipes.
     */
    private String cleanMessage(String msg) {
        if (msg == null) return null;
        String s = msg.trim();
        while (s.startsWith("|")) s = s.substring(1).trim();
        return s;
    }

    /** Infer SOAP call success from response XML content. */
    private String deriveSoapStatus(String body) {
        if (body == null) return "PENDING";
        String u = body.toUpperCase();
        if (u.contains("FAULT") || u.contains("<FAILURE>") || u.contains("<FAILED>")) return "SERVER_ERROR";
        return "SUCCESS";
    }

    private boolean matchesSearchId(ParsedLogEntry e, String searchId) {
        if (searchId == null) return false;
        return searchId.equalsIgnoreCase(e.getCorrelationId())
                || searchId.equalsIgnoreCase(e.getTid())
                || (e.getMessage() != null && e.getMessage().contains(searchId));
    }

    private String extractServiceName(String podName) {
        if (podName == null) return "unknown";
        int last = podName.lastIndexOf('-');
        if (last > 0) {
            int second = podName.lastIndexOf('-', last - 1);
            if (second > 0) return podName.substring(0, second);
        }
        return podName;
    }

    // -------------------------------------------------------------------------
    // Mutable call-state holder (built incrementally, converted at emit time)
    // -------------------------------------------------------------------------

    private static class CallState {
        final String podName, serviceName, thread, correlationId;
        final LocalDateTime requestTimestamp;
        final String method, url;

        String requestHeaders, requestBody;
        LocalDateTime responseTimestamp;
        Integer responseStatus;
        String responseStatusText, responseHeaders, responseBody;
        boolean inResponse = false;
        String forcedStatus = null;
        private static int counter = 0;

        CallState(String podName, String serviceName, String thread, String correlationId,
                  LocalDateTime requestTimestamp, String method, String url) {
            this.podName = podName;
            this.serviceName = serviceName;
            this.thread = thread;
            this.correlationId = correlationId;
            this.requestTimestamp = requestTimestamp;
            this.method = method;
            this.url = url;
        }

        DownstreamApiCall build() {
            Long duration = null;
            if (requestTimestamp != null && responseTimestamp != null)
                duration = Duration.between(requestTimestamp, responseTimestamp).toMillis();

            return DownstreamApiCall.builder()
                    .id(podName + "-" + thread + "-" + (++counter))
                    .podName(podName)
                    .serviceName(serviceName)
                    .thread(thread)
                    .correlationId(correlationId)
                    .requestTimestamp(requestTimestamp)
                    .method(method)
                    .url(url)
                    .requestHeaders(requestHeaders)
                    .requestBody(requestBody)
                    .responseTimestamp(responseTimestamp)
                    .responseStatus(responseStatus)
                    .responseStatusText(responseStatusText)
                    .responseHeaders(responseHeaders)
                    .responseBody(responseBody)
                    .durationMs(duration)
                    .callStatus(forcedStatus != null ? forcedStatus : deriveStatus())
                    .build();
        }

        private String deriveStatus() {
            if (responseStatus != null) {
                if (responseStatus < 400) return "SUCCESS";
                if (responseStatus < 500) return "CLIENT_ERROR";
                return "SERVER_ERROR";
            }
            // SOAP call: response captured means success
            if (responseBody != null || responseStatusText != null) return "SUCCESS";
            return "PENDING";
        }
    }
}
