package com.yourorg.deploy.service;

import com.yourorg.deploy.model.DownstreamApiCall;
import com.yourorg.deploy.model.ParsedLogEntry;
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
 * Two call styles are supported. In both, we require *evidence* (request XML,
 * response XML, BEM context, or an explicit HTTP verb + URL) before emitting a
 * record — a bare "ClassName :: method" line is not enough. This avoids the
 * noise from internal *ServiceImpl / *Filter / *Util / *Mapper classes.
 *
 * ── SOAP style (BEM) ──────────────────────────────────────────────────────────
 *   BEMRequestBuilder :: buildBEMRequestHeader :: ...        (context, buffered)
 *   SomethingClient :: methodName ::                         (call start)
 *   Request for XRequest in XML format : <?xml ...>          (body, possibly multi-line)
 *   Response for XResponse in XML format : <?xml ...>        (DEBUG-only)
 *
 * Suffix is restricted to `Client` because every real BEM downstream ends in
 * Client (IndividualCustomerRelationshipClient, NFAccountManagementClient,
 * GetCustomerDetailsW3Client, ...). *Service / *Impl / *Handler are internal.
 *
 * ── REST style ────────────────────────────────────────────────────────────────
 *   POST http://host/path                                    (inline method+URL)
 *   HTTP Method : POST   /   uri : https://...               (split across lines)
 *   ods request method : POST and uri : https://...          (OSSFacade variant)
 *   Response Status: 200 OK  /  Response received with Status code : 200
 *
 * REST calls always carry an explicit HTTP verb + URL so false-positive risk
 * is low — they're emitted on first sight without needing extra proof.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DownstreamCallParserService {

    private final LogParserService logParserService;

    /** Cap each body at ~32KB to prevent runaway memory on huge SOAP responses. */
    private static final int MAX_BODY_BYTES = 32 * 1024;

    // -------------------------------------------------------------------------
    // SOAP patterns
    // -------------------------------------------------------------------------

    /** BEM request header builder — buffered as "saw BEM context" evidence. */
    private static final Pattern BEM_DETAIL = Pattern.compile(
            "(?i)BEMRequestBuilder\\s*::\\s*(.+)"
    );

    /** SOAP client call — only *Client suffix (everything else is internal). */
    private static final Pattern SOAP_START = Pattern.compile(
            "(?i)(\\w+Client)\\s*::\\s*(\\w+)"
    );

    /** SOAP/XML request body line (standalone or tail of SOAP_START line). */
    private static final Pattern SOAP_REQUEST_XML = Pattern.compile(
            "(?i)request\\s+for\\s+(\\w+)\\s+in\\s+(?:xml|json|soap)\\s+format\\s*:\\s*(.+)"
    );

    /** SOAP/XML response body line (DEBUG-only typically). */
    private static final Pattern SOAP_RESPONSE = Pattern.compile(
            "(?i)response\\s+for\\s+(\\w+)\\s+in\\s+(?:xml|json|soap)\\s+format\\s*:\\s*(.+)"
    );

    /** Markers that terminate multi-line XML body capture. */
    private static final Pattern BODY_TERMINATOR = Pattern.compile(
            "(?i)(^\\w+Client\\s*::)|(^BEMRequestBuilder\\s*::)|(exiting\\b)|(execution\\s+time)|(^\\s*>>\\s)|(^\\s*<<\\s)"
    );

    /** Extract the SOAP target URL from request-body XML namespaces. */
    private static final Pattern XML_NS_URL = Pattern.compile(
            "xmlns(?::\\w+)?\\s*=\\s*\"(https?://[^\"]+)\""
    );

    // -------------------------------------------------------------------------
    // REST patterns
    // -------------------------------------------------------------------------

    /** Inline method + URL: "POST http://...", ">>> GET http://...", "HTTP POST http://...". */
    private static final Pattern REQ_INLINE = Pattern.compile(
            "(?i)(?:>>>\\s*|calling\\s*:?\\s*|outbound\\s+|http\\s+)?" +
            "(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+(https?://\\S+)"
    );

    /** OSSFacade variant: "ods request method : POST and uri : https://...". */
    private static final Pattern REQ_OSS = Pattern.compile(
            "(?i)(?:ods\\s+)?request\\s+method\\s*:?\\s*(GET|POST|PUT|DELETE|PATCH)\\s+and\\s+uri\\s*:?\\s*(https?://\\S+)"
    );

    /** Split URI on its own line: "URI: http://..." or "url = http://...". */
    private static final Pattern REQ_URI_ONLY = Pattern.compile(
            "(?i)(?:request\\s+)?(?:url|uri)\\s*[=:]\\s*(https?://\\S+)"
    );

    /** Split "Method : POST" / "HTTP Method : POST" on its own line. */
    private static final Pattern REQ_METHOD_ONLY = Pattern.compile(
            "(?i)(?:http\\s+)?method\\s*[=:]\\s*(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)"
    );

    /** HTTP response status lines (various interceptor formats). */
    private static final Pattern RESP_STATUS = Pattern.compile(
            "(?i)(?:response\\s+status\\s*[=:]?\\s*|resp(?:onse)?\\s+received\\s+with\\s+status\\s+code\\s*[=:]?\\s*|<<<\\s*|status\\s+code\\s*[=:]?\\s*|status[=:]\\s*)(\\d{3})(?:\\s+(.*))?"
    );

    /** Spring RestTemplate built-in: "Response 200 OK". */
    private static final Pattern RESP_SPRING = Pattern.compile(
            "(?i)response\\s+(\\d{3})\\s+(\\S.*)"
    );

    /** Network-level errors. */
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
            calls.addAll(extractCallsFromThread(entries, podName, serviceName, searchId));
        }

        log.info("Extracted {} downstream calls for searchId={} pod={}", calls.size(), searchId, podName);
        return calls;
    }

    // -------------------------------------------------------------------------
    // State machine — one run per thread group
    // -------------------------------------------------------------------------

    private List<DownstreamApiCall> extractCallsFromThread(
            List<ParsedLogEntry> entries, String podName, String serviceName, String searchId) {

        List<DownstreamApiCall> result = new ArrayList<>();
        CallState state = null;
        String pendingUri = null;
        boolean sawBemContext = false;
        // "soap" or "rest" — SOAP needs proof before emit; REST emits on sight.
        String lastCaptureMode = null;

        for (ParsedLogEntry e : entries) {
            String msg = cleanMessage(e.getMessage());
            if (msg == null || msg.isBlank()) continue;

            // Multi-line body capture — if the current SOAP state is actively
            // absorbing a request or response body, append until a terminator.
            if (state != null && state.capturing != CallState.Capture.NONE) {
                if (!BODY_TERMINATOR.matcher(msg).find() && !isNewCallStart(msg)) {
                    state.appendBody(msg);
                    continue;
                }
                // Terminator encountered — stop capturing, fall through to reclassify.
                state.capturing = CallState.Capture.NONE;
            }

            // ── BEM context (buffered as evidence, not a call start) ──────────
            if (BEM_DETAIL.matcher(msg).find()) {
                sawBemContext = true;
                continue;
            }

            // ── SOAP client method invocation ─────────────────────────────────
            Matcher soapMatch = SOAP_START.matcher(msg);
            if (soapMatch.find()) {
                emitIfProven(state, result);
                state = new CallState(podName, serviceName, searchId,
                        e.getTimestamp(), "SOAP",
                        "soap:" + soapMatch.group(1) + "::" + soapMatch.group(2));
                state.sawBemContext = sawBemContext;
                sawBemContext = false;
                lastCaptureMode = "soap";

                // Same line may also carry "Request for X in XML format : <body>"
                Matcher inlineReq = SOAP_REQUEST_XML.matcher(msg);
                if (inlineReq.find()) {
                    state.requestBody = clip(inlineReq.group(2));
                    state.capturing = CallState.Capture.REQUEST;
                    state.sawEvidence = true;
                }
                // Same line may also carry "Response for X in XML format : <body>"
                Matcher inlineResp = SOAP_RESPONSE.matcher(msg);
                if (inlineResp.find()) {
                    state.responseBody = clip(inlineResp.group(2));
                    state.capturing = CallState.Capture.RESPONSE;
                    state.sawEvidence = true;
                    state.forcedStatus = deriveSoapStatus(state.responseBody);
                }
                continue;
            }

            // ── Standalone "Request for X ..." (two-line SOAP pattern) ────────
            if (state != null && "soap".equals(lastCaptureMode)) {
                Matcher reqXml = SOAP_REQUEST_XML.matcher(msg);
                if (reqXml.find()) {
                    state.requestBody = clip(reqXml.group(2));
                    state.capturing = CallState.Capture.REQUEST;
                    state.sawEvidence = true;
                    continue;
                }
                Matcher respXml = SOAP_RESPONSE.matcher(msg);
                if (respXml.find()) {
                    state.responseBody = clip(respXml.group(2));
                    state.capturing = CallState.Capture.RESPONSE;
                    state.sawEvidence = true;
                    state.forcedStatus = deriveSoapStatus(state.responseBody);
                    continue;
                }
            }

            // ── REST: OSSFacade-style combined line ───────────────────────────
            Matcher oss = REQ_OSS.matcher(msg);
            if (oss.find()) {
                emitIfProven(state, result);
                state = new CallState(podName, serviceName, searchId,
                        e.getTimestamp(), oss.group(1).toUpperCase(), oss.group(2));
                state.sawEvidence = true;
                lastCaptureMode = "rest";
                pendingUri = null;
                continue;
            }

            // ── REST: inline method + URL ─────────────────────────────────────
            Matcher inline = REQ_INLINE.matcher(msg);
            if (inline.find()) {
                emitIfProven(state, result);
                state = new CallState(podName, serviceName, searchId,
                        e.getTimestamp(), inline.group(1).toUpperCase(), inline.group(2));
                state.sawEvidence = true;
                lastCaptureMode = "rest";
                pendingUri = null;
                continue;
            }

            // ── REST: split "url : http://..." (pending until method arrives) ─
            Matcher uriOnly = REQ_URI_ONLY.matcher(msg);
            if (uriOnly.find()) {
                pendingUri = uriOnly.group(1);
                continue;
            }

            // ── REST: split "method : POST" (pairs with pending URI) ──────────
            Matcher methodOnly = REQ_METHOD_ONLY.matcher(msg);
            if (methodOnly.find() && pendingUri != null) {
                emitIfProven(state, result);
                state = new CallState(podName, serviceName, searchId,
                        e.getTimestamp(), methodOnly.group(1).toUpperCase(), pendingUri);
                state.sawEvidence = true;
                lastCaptureMode = "rest";
                pendingUri = null;
                continue;
            }

            if (state == null) continue;

            // ── REST response status ──────────────────────────────────────────
            Matcher respSt = RESP_STATUS.matcher(msg);
            if (respSt.find()) {
                int code = Integer.parseInt(respSt.group(1));
                state.forcedStatus = code < 400 ? "SUCCESS"
                        : code < 500 ? "CLIENT_ERROR" : "SERVER_ERROR";
                continue;
            }
            Matcher respSp = RESP_SPRING.matcher(msg);
            if (respSp.find()) {
                int code = Integer.parseInt(respSp.group(1));
                state.forcedStatus = code < 400 ? "SUCCESS"
                        : code < 500 ? "CLIENT_ERROR" : "SERVER_ERROR";
                continue;
            }

            // ── Network errors ────────────────────────────────────────────────
            if (TIMEOUT.matcher(msg).find()) {
                state.forcedStatus = "TIMEOUT";
                state.sawEvidence = true;
                emitIfProven(state, result);
                state = null;
            } else if (CONN_ERROR.matcher(msg).find()) {
                state.forcedStatus = "CONN_ERROR";
                state.sawEvidence = true;
                emitIfProven(state, result);
                state = null;
            }
        }

        emitIfProven(state, result);
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isNewCallStart(String msg) {
        return SOAP_START.matcher(msg).find()
                || REQ_INLINE.matcher(msg).find()
                || REQ_OSS.matcher(msg).find();
    }

    /** Emit the call only if we have evidence it's a real external hop. */
    private void emitIfProven(CallState state, List<DownstreamApiCall> result) {
        if (state == null) return;
        if (!state.sawEvidence && !state.sawBemContext) return;
        result.add(state.build());
    }

    /** Pipe-delimited log format uses a double-pipe before the message. */
    private String cleanMessage(String msg) {
        if (msg == null) return null;
        String s = msg.trim();
        while (s.startsWith("|")) s = s.substring(1).trim();
        return s;
    }

    private String clip(String s) {
        if (s == null) return null;
        return s.length() > MAX_BODY_BYTES ? s.substring(0, MAX_BODY_BYTES) : s;
    }

    /** Infer SOAP call success from response XML content. */
    private String deriveSoapStatus(String body) {
        if (body == null) return "SUCCESS";
        String u = body.toUpperCase();
        if (u.contains("FAULT") || u.contains("<FAILURE>") || u.contains("<FAILED>")) return "SERVER_ERROR";
        return "SUCCESS";
    }

    /** Extract the first `xmlns...="http..."` namespace URL from a SOAP body. */
    private static String extractSoapUrl(String body) {
        if (body == null) return null;
        Matcher m = XML_NS_URL.matcher(body);
        while (m.find()) {
            String url = m.group(1);
            // Skip XMLSchema / W3 boilerplate — we want the service namespace.
            if (!url.contains("w3.org") && !url.contains("xmlsoap.org")) return url;
        }
        return null;
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
    // Mutable call-state holder
    // -------------------------------------------------------------------------

    private static class CallState {
        enum Capture { NONE, REQUEST, RESPONSE }

        final String podName, serviceName, correlationId;
        final LocalDateTime requestTimestamp;
        final String method;
        String url;

        String requestBody, responseBody;
        Capture capturing = Capture.NONE;
        boolean sawBemContext = false;
        boolean sawEvidence = false;
        String forcedStatus = null;
        private static int counter = 0;

        CallState(String podName, String serviceName, String correlationId,
                  LocalDateTime requestTimestamp, String method, String url) {
            this.podName = podName;
            this.serviceName = serviceName;
            this.correlationId = correlationId;
            this.requestTimestamp = requestTimestamp;
            this.method = method;
            this.url = url;
        }

        void appendBody(String line) {
            String target;
            if (capturing == Capture.REQUEST) {
                target = (requestBody == null ? "" : requestBody) + "\n" + line;
                if (target.length() > MAX_BODY_BYTES) target = target.substring(0, MAX_BODY_BYTES);
                requestBody = target;
            } else if (capturing == Capture.RESPONSE) {
                target = (responseBody == null ? "" : responseBody) + "\n" + line;
                if (target.length() > MAX_BODY_BYTES) target = target.substring(0, MAX_BODY_BYTES);
                responseBody = target;
            }
        }

        DownstreamApiCall build() {
            String finalUrl = url;
            // For SOAP calls, prefer the real target URL extracted from the
            // request XML's xmlns namespace over the synthetic "soap:..." label.
            if ("SOAP".equals(method) && requestBody != null) {
                String nsUrl = extractSoapUrl(requestBody);
                if (nsUrl != null) finalUrl = nsUrl;
            }

            String status = forcedStatus != null
                    ? forcedStatus
                    : (responseBody != null && isSoapFault(responseBody) ? "SERVER_ERROR" : "SUCCESS");

            return DownstreamApiCall.builder()
                    .id(podName + "-" + (++counter))
                    .podName(podName)
                    .serviceName(serviceName)
                    .correlationId(correlationId)
                    .requestTimestamp(requestTimestamp)
                    .method(method)
                    .url(finalUrl)
                    .requestBody(requestBody)
                    .responseBody(responseBody)
                    .callStatus(status)
                    .build();
        }

        private static boolean isSoapFault(String body) {
            String u = body.toUpperCase();
            return u.contains("FAULT") || u.contains("<FAILURE>") || u.contains("<FAILED>");
        }
    }
}
