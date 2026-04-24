package com.yourorg.deploy.service;

import com.yourorg.deploy.model.DownstreamApiCall;
import com.yourorg.deploy.model.ParsedLogEntry;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Extracts structured downstream HTTP/SOAP call records from raw pod logs.
 *
 * The application architecture is:  Controller → Service → (Facade | Client |
 * BEMHelper) → external hop. Only the last layer is a real downstream; every
 * *ServiceImpl / *Mapper / *Util / *Filter / *Handler in between is internal
 * orchestration and must never produce a row here.
 *
 * ── Pattern C — SOAP (BEM / *Client) ──────────────────────────────────────────
 *   BEMRequestBuilder :: buildBEMRequestHeader :: ...          (context, buffered)
 *   SomethingClient :: methodName ::                           (call start)
 *   Request for XRequest in XML format : <?xml ...>            (body, may be multi-line)
 *   Response for XResponse in XML format : <?xml ...>          (DEBUG-only)
 *
 * ── Pattern A — REST Facade with explicit call-group id ───────────────────────
 *   [ef186bb1-…] :: ODSFacade::executeOdsApiCall : START
 *   [ef186bb1-…] :: ODSFacade::executeOdsApiCall : request method : POST and url : https://…
 *   [ef186bb1-…] :: ODSFacade::executeOdsApiCall : Response received with Status code : 200
 *
 * ── Pattern B — REST Facade with embedded UUID prefix (async, empty brackets) ─
 *   38e095c8-… :ConsentFacade :: executeConsentApiCall : Sending request to Consent DB
 *   responseJson is : { ..., "statusCode":201, "errors":null, ... }
 *   38e095c8-… :ConsentFacade :: executeConsentApiCall : Response received with Status code : 201
 *
 * Grouping preference for state-machine runs is callGroupId → correlationId →
 * tid → thread, so async executions that hop pools still form one call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DownstreamCallParserService {

    private final LogParserService logParserService;

    /** Cap each body at ~32KB to prevent runaway memory on huge SOAP responses. */
    private static final int MAX_BODY_BYTES = 32 * 1024;

    // -------------------------------------------------------------------------
    // Call-boundary detection — only these suffixes are real downstreams
    // -------------------------------------------------------------------------

    /** BEM request header builder — context only, never a call on its own. */
    private static final Pattern BEM_DETAIL = Pattern.compile(
            "(?i)BEMRequestBuilder\\s*::\\s*(.+)"
    );

    /**
     * Downstream boundary: Facade (REST), Client (SOAP), or BEMHelper (SOAP).
     * The lazy \w*? lets the suffix stand alone (bare "BEMHelper") or sit on a
     * prefix ("ODSFacade", "IndividualCustomerRelationshipClient").
     */
    private static final Pattern DS_START = Pattern.compile(
            "(?i)(\\w*?(?:Facade|Client|BEMHelper))\\s*::\\s*(\\w+)"
    );

    /**
     * Framework *Client classes that look like downstreams but aren't —
     * they're HTTP plumbing, not business hops. Matched on class name only.
     */
    private static final Pattern FRAMEWORK_CLIENT = Pattern.compile(
            "(?i)^(HttpClient|RestClient|RestTemplate|FeignClient|WebClient|OkHttpClient)$"
    );

    // -------------------------------------------------------------------------
    // SOAP body patterns
    // -------------------------------------------------------------------------

    private static final Pattern SOAP_REQUEST_XML = Pattern.compile(
            "(?i)request\\s+for\\s+(\\w+)\\s+in\\s+(?:xml|json|soap)\\s+format\\s*:\\s*(.+)"
    );

    private static final Pattern SOAP_RESPONSE_XML = Pattern.compile(
            "(?i)response\\s+for\\s+(\\w+)\\s+in\\s+(?:xml|json|soap)\\s+format\\s*:\\s*(.+)"
    );

    /** Extract the SOAP target URL from request-body XML namespaces. */
    private static final Pattern XML_NS_URL = Pattern.compile(
            "xmlns(?::\\w+)?\\s*=\\s*\"(https?://[^\"]+)\""
    );

    // -------------------------------------------------------------------------
    // REST body patterns (both Facade variants)
    // -------------------------------------------------------------------------

    /** Pattern A: "request method : POST and url|uri : https://…" (url OR uri). */
    private static final Pattern REQ_METHOD_URL = Pattern.compile(
            "(?i)(?:ods\\s+)?request\\s+method\\s*:?\\s*(GET|POST|PUT|DELETE|PATCH)"
          + "\\s+and\\s+(?:url|uri)\\s*:?\\s*(https?://\\S+)"
    );

    /** Inline "POST http://..." (Spring interceptor / generic outbound log). */
    private static final Pattern REQ_INLINE = Pattern.compile(
            "(?i)(?:>>>\\s*|calling\\s*:?\\s*|outbound\\s+|http\\s+)?"
          + "(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+(https?://\\S+)"
    );

    /** Pattern B: "Sending request to <destination>" — start-marker for DB-backed Facades. */
    private static final Pattern REQ_SENDING = Pattern.compile(
            "(?i)sending\\s+request\\s+to\\s+\\S+"
    );

    /** "responseJson is : {…}" — captures the REST response body (may be multi-line). */
    private static final Pattern RESP_JSON = Pattern.compile(
            "(?i)response(?:json|body)\\s+is\\s*:?\\s*(.+)"
    );

    /** HTTP status from interceptor logs — various phrasings. */
    private static final Pattern RESP_STATUS = Pattern.compile(
            "(?i)(?:response\\s+status\\s*[=:]?\\s*"
          + "|resp(?:onse)?\\s+received\\s+with\\s+status\\s+code\\s*[=:]?\\s*"
          + "|<<<\\s*|status\\s+code\\s*[=:]?\\s*|status[=:]\\s*)(\\d{3})(?:\\s+(.*))?"
    );

    // -------------------------------------------------------------------------
    // Terminators, exceptions, body-capture cutoff markers
    // -------------------------------------------------------------------------

    /**
     * Markers that end multi-line body capture. Includes the "other-body-kind"
     * markers so that capturing a REQUEST XML doesn't swallow the RESPONSE
     * XML line (which earlier caused response bodies to be glued to requests).
     */
    private static final Pattern BODY_TERMINATOR = Pattern.compile(
            "(?i)(^\\w+(?:Facade|Client|BEMHelper)\\s*::)"
          + "|(^BEMRequestBuilder\\s*::)"
          + "|(request\\s+for\\s+\\w+\\s+in\\s+(?:xml|json|soap)\\s+format)"
          + "|(response\\s+for\\s+\\w+\\s+in\\s+(?:xml|json|soap)\\s+format)"
          + "|(response(?:json|body)\\s+is\\s*:)"
          + "|(resp(?:onse)?\\s+received\\s+with\\s+status\\s+code)"
          + "|(building\\s+(?:success|error)\\s+response)"
          + "|(^exiting\\b)|(^entering\\b)"
          + "|(execution\\s+time)"
          + "|(^\\s*>>\\s)|(^\\s*<<\\s)"
    );

    /** Network timeouts → TIMEOUT. */
    private static final Pattern TIMEOUT = Pattern.compile(
            "(?i)SocketTimeoutException|Read\\s+timed\\s+out|connection\\s+timed\\s+out|connect\\s+timeout"
    );

    /** Connection-refused family → CONN_ERROR. */
    private static final Pattern CONN_ERROR = Pattern.compile(
            "(?i)Connection\\s+refused|ConnectException|No\\s+route\\s+to\\s+host|UnknownHostException"
    );

    /**
     * Generic exception / stack-trace markers — catches anything ending in
     * "Exception" (optionally with a package prefix) plus "Caused by:" and
     * bare "at com.…" frames. Used to flag SERVER_ERROR when an exception
     * lands inside an open call window.
     */
    private static final Pattern GENERIC_EXCEPTION = Pattern.compile(
            "(?i)(?:^|[\\s:])(?:[a-z][\\w.]*\\.)?\\w+Exception\\b"
          + "|caused\\s+by\\s*:"
          + "|(?:^|\\s)at\\s+[a-zA-Z_][\\w.$]*\\([^)]*\\)"
    );

    /** Business-level failure signals inside a JSON response body. */
    private static final Pattern JSON_BUSINESS_ERROR = Pattern.compile(
            "(?i)\"errors\"\\s*:\\s*\\[\\s*\\{"
          + "|\"statusCode\"\\s*:\\s*\"?(?:FAIL|FAILED|ERROR)"
          + "|\"responseCode\"\\s*:\\s*\"?7\\d{2}"
    );

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public List<DownstreamApiCall> parseDownstreamCalls(
            String rawLogs, String searchId, String podName) {

        if (rawLogs == null || rawLogs.isEmpty()) return List.of();

        List<ParsedLogEntry> allEntries;
        try {
            allEntries = logParserService.parseLogLines(rawLogs);
        } catch (Exception e) {
            log.warn("Log parse failed for pod {}: {}", podName, e.getMessage());
            return List.of();
        }

        List<ParsedLogEntry> relevant = allEntries.stream()
                .filter(e -> matchesSearchId(e, searchId))
                .collect(Collectors.toList());

        if (relevant.isEmpty()) return List.of();

        // Group by callGroupId first (Facade logs carry a per-call UUID that
        // survives thread hops), falling back through correlationId → tid →
        // thread. This keeps one async call as one state-machine run.
        Map<String, List<ParsedLogEntry>> groups = relevant.stream()
                .collect(Collectors.groupingBy(
                        this::groupKey,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        String serviceName = extractServiceName(podName);
        AtomicInteger idCounter = new AtomicInteger();
        List<DownstreamApiCall> calls = new ArrayList<>();

        for (Map.Entry<String, List<ParsedLogEntry>> group : groups.entrySet()) {
            List<ParsedLogEntry> entries = group.getValue();
            entries.sort(Comparator.comparing(ParsedLogEntry::getTimestamp,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            try {
                calls.addAll(extractCallsFromGroup(
                        entries, podName, serviceName, searchId, idCounter));
            } catch (Exception e) {
                log.warn("Extraction failed for group {} in pod {}: {}",
                        group.getKey(), podName, e.getMessage());
            }
        }

        log.info("Extracted {} downstream calls for searchId={} pod={}",
                calls.size(), searchId, podName);
        return calls;
    }

    // -------------------------------------------------------------------------
    // State machine — one run per logical call group
    // -------------------------------------------------------------------------

    private List<DownstreamApiCall> extractCallsFromGroup(
            List<ParsedLogEntry> entries, String podName, String serviceName,
            String searchId, AtomicInteger idCounter) {

        List<DownstreamApiCall> result = new ArrayList<>();
        CallState state = null;
        boolean sawBemContext = false;

        for (ParsedLogEntry e : entries) {
            String msg;
            try {
                msg = cleanMessage(e.getMessage());
            } catch (Exception ex) {
                log.debug("cleanMessage threw on line {}: {}", e.getRawLine(), ex.getMessage());
                continue;
            }
            if (msg == null || msg.isBlank()) continue;

            try {
                // 1) Active body capture — keep appending until a terminator or
                //    a new call start. Terminators include response/request/status
                //    markers so we don't glue a response body onto a request.
                if (state != null && state.capturing != CallState.Capture.NONE) {
                    if (!BODY_TERMINATOR.matcher(msg).find() && !isNewCallStart(msg)) {
                        state.appendBody(msg);
                        continue;
                    }
                    state.capturing = CallState.Capture.NONE;
                    // fall through to reclassify the terminator line itself
                }

                // 2) Error / exception lines — mark the open call, don't emit yet.
                if (state != null && TIMEOUT.matcher(msg).find()) {
                    state.forcedStatus = "TIMEOUT";
                    state.sawEvidence = true;
                    continue;
                }
                if (state != null && CONN_ERROR.matcher(msg).find()) {
                    state.forcedStatus = "CONN_ERROR";
                    state.sawEvidence = true;
                    continue;
                }
                if (state != null && "ERROR".equalsIgnoreCase(e.getLogLevel())
                        && state.forcedStatus == null) {
                    state.forcedStatus = "SERVER_ERROR";
                    continue;
                }
                if (state != null && GENERIC_EXCEPTION.matcher(msg).find()
                        && state.forcedStatus == null) {
                    state.forcedStatus = "SERVER_ERROR";
                    continue;
                }

                // 3) BEM context — buffer as corroboration for the next SOAP call.
                if (BEM_DETAIL.matcher(msg).find()) {
                    sawBemContext = true;
                    continue;
                }

                // 4) Downstream boundary: Facade / Client / BEMHelper.
                Matcher dsMatch = DS_START.matcher(msg);
                if (dsMatch.find() && !FRAMEWORK_CLIENT.matcher(dsMatch.group(1)).matches()) {
                    boolean isSoap = dsMatch.group(1).toLowerCase()
                            .matches(".*(client|bemhelper)$");

                    // New call starts — only emit the previous one if we have proof.
                    emitIfProven(state, result, idCounter);

                    state = new CallState(podName, serviceName, searchId,
                            e.getTimestamp(),
                            isSoap ? "SOAP" : "REST",
                            (isSoap ? "soap:" : "rest:")
                                    + dsMatch.group(1) + "::" + dsMatch.group(2));
                    state.sawBemContext = sawBemContext;
                    sawBemContext = false;

                    // Same line may also carry a request body / URL / status — try all.
                    absorbSameLineBody(state, msg);
                    continue;
                }

                // 5) Standalone body/status lines — attach to the open call.
                if (state != null) {
                    if (absorbBodyLine(state, msg)) continue;

                    // "url : http://…" on its own line, awaiting a method line.
                    Matcher methUrl = REQ_METHOD_URL.matcher(msg);
                    if (methUrl.find()) {
                        if ("REST".equals(state.method)) {
                            state.url = methUrl.group(2);
                            state.httpMethod = methUrl.group(1).toUpperCase();
                            state.sawEvidence = true;
                        }
                        continue;
                    }

                    // "Sending request to …" — mark as REST evidence for Pattern B.
                    if (REQ_SENDING.matcher(msg).find()) {
                        if ("REST".equals(state.method)) state.sawEvidence = true;
                        continue;
                    }

                    // Status code line.
                    Matcher rs = RESP_STATUS.matcher(msg);
                    if (rs.find()) {
                        int code = Integer.parseInt(rs.group(1));
                        state.forcedStatus = code < 400 ? "SUCCESS"
                                : code < 500 ? "CLIENT_ERROR" : "SERVER_ERROR";
                        continue;
                    }
                }

                // 6) No open state — a bare inline "POST http://…" may still be
                //    an orphan REST call (no Facade wrapper). Record it.
                if (state == null) {
                    Matcher inline = REQ_INLINE.matcher(msg);
                    if (inline.find()) {
                        state = new CallState(podName, serviceName, searchId,
                                e.getTimestamp(), "REST", inline.group(2));
                        state.httpMethod = inline.group(1).toUpperCase();
                        state.sawEvidence = true;
                    }
                }
            } catch (Exception ex) {
                log.warn("Skipping malformed log line: {}", ex.getMessage());
            }
        }

        emitIfProven(state, result, idCounter);
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Absorb any request/response body or status that sits on the same line as DS_START. */
    private void absorbSameLineBody(CallState state, String msg) {
        absorbBodyLine(state, msg);
        Matcher mu = REQ_METHOD_URL.matcher(msg);
        if (mu.find() && "REST".equals(state.method)) {
            state.url = mu.group(2);
            state.httpMethod = mu.group(1).toUpperCase();
            state.sawEvidence = true;
        }
        Matcher rs = RESP_STATUS.matcher(msg);
        if (rs.find()) {
            int code = Integer.parseInt(rs.group(1));
            state.forcedStatus = code < 400 ? "SUCCESS"
                    : code < 500 ? "CLIENT_ERROR" : "SERVER_ERROR";
        }
    }

    /** Match a standalone request/response body line and attach to state. */
    private boolean absorbBodyLine(CallState state, String msg) {
        Matcher reqXml = SOAP_REQUEST_XML.matcher(msg);
        if (reqXml.find()) {
            state.requestBody = clip(reqXml.group(2));
            state.capturing = CallState.Capture.REQUEST;
            state.sawEvidence = true;
            return true;
        }
        Matcher respXml = SOAP_RESPONSE_XML.matcher(msg);
        if (respXml.find()) {
            state.responseBody = clip(respXml.group(2));
            state.capturing = CallState.Capture.RESPONSE;
            state.sawEvidence = true;
            return true;
        }
        Matcher respJson = RESP_JSON.matcher(msg);
        if (respJson.find()) {
            state.responseBody = clip(respJson.group(1));
            state.capturing = CallState.Capture.RESPONSE;
            state.sawEvidence = true;
            return true;
        }
        return false;
    }

    private boolean isNewCallStart(String msg) {
        Matcher m = DS_START.matcher(msg);
        return m.find() && !FRAMEWORK_CLIENT.matcher(m.group(1)).matches();
    }

    /**
     * Emit the call only if we have real evidence: a body was captured, a URL
     * was seen, or a terminal status was forced. BEM context alone is not
     * enough — it used to cause ghost entries from bare Client::method lines.
     */
    private void emitIfProven(CallState state, List<DownstreamApiCall> result,
                              AtomicInteger idCounter) {
        if (state == null || !state.sawEvidence) return;
        result.add(state.build(idCounter.incrementAndGet()));
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

    /** Infer SOAP call success — checks XML faults and business return codes. */
    private static String deriveSoapStatus(String body) {
        if (body == null) return "SUCCESS";
        String u = body.toUpperCase();
        if (u.contains("FAULT") || u.contains("<FAILURE>") || u.contains("<FAILED>")) return "SERVER_ERROR";
        return "SUCCESS";
    }

    /** Extract the first `xmlns…="http…"` namespace URL from a SOAP body. */
    private static String extractSoapUrl(String body) {
        if (body == null) return null;
        Matcher m = XML_NS_URL.matcher(body);
        while (m.find()) {
            String url = m.group(1);
            if (!url.contains("w3.org") && !url.contains("xmlsoap.org")) return url;
        }
        return null;
    }

    /**
     * Match searchId against structured fields only — no message.contains()
     * fallback. correlationId is promoted from an embedded UUID when the
     * logger bracket is empty (see LogParserService.tryAppLog), so equality
     * is sufficient.
     */
    private boolean matchesSearchId(ParsedLogEntry e, String searchId) {
        if (searchId == null) return false;
        if (searchId.equalsIgnoreCase(e.getCorrelationId())
                || searchId.equalsIgnoreCase(e.getTid())) return true;
        return e.getDerivedFields() != null
                && searchId.equalsIgnoreCase(e.getDerivedFields().get("callGroupId"));
    }

    /** callGroupId → correlationId → tid → thread. */
    private String groupKey(ParsedLogEntry e) {
        if (e.getDerivedFields() != null) {
            String cg = e.getDerivedFields().get("callGroupId");
            if (cg != null && !cg.isEmpty()) return "cg:" + cg;
        }
        if (e.getCorrelationId() != null) return "corr:" + e.getCorrelationId();
        if (e.getTid() != null) return "tid:" + e.getTid();
        return "thr:" + (e.getThread() != null ? e.getThread() : "unknown");
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

    private static final class CallState {
        enum Capture { NONE, REQUEST, RESPONSE }

        final String podName;
        final String serviceName;
        final String correlationId;
        final LocalDateTime requestTimestamp;
        final String method;   // "SOAP" or "REST"
        String httpMethod;     // REST verb: GET / POST / …
        String url;

        String requestBody;
        String responseBody;
        Capture capturing = Capture.NONE;
        boolean sawBemContext = false;
        boolean sawEvidence = false;
        String forcedStatus = null;

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
            if (capturing == Capture.REQUEST) {
                String target = (requestBody == null ? "" : requestBody) + "\n" + line;
                if (target.length() > MAX_BODY_BYTES) target = target.substring(0, MAX_BODY_BYTES);
                requestBody = target;
            } else if (capturing == Capture.RESPONSE) {
                String target = (responseBody == null ? "" : responseBody) + "\n" + line;
                if (target.length() > MAX_BODY_BYTES) target = target.substring(0, MAX_BODY_BYTES);
                responseBody = target;
            }
        }

        DownstreamApiCall build(int id) {
            String finalUrl = url;
            if ("SOAP".equals(method) && requestBody != null) {
                String nsUrl = extractSoapUrl(requestBody);
                if (nsUrl != null) finalUrl = nsUrl;
            }

            String status;
            if (forcedStatus != null) {
                status = forcedStatus;
            } else if ("SOAP".equals(method)) {
                status = deriveSoapStatus(responseBody);
            } else if (responseBody != null
                    && JSON_BUSINESS_ERROR.matcher(responseBody).find()) {
                status = "SERVER_ERROR";
            } else {
                status = "SUCCESS";
            }

            // Prefer the concrete HTTP verb over the "REST" placeholder when known.
            String methodOut = "REST".equals(method) && httpMethod != null ? httpMethod : method;

            return DownstreamApiCall.builder()
                    .id(podName + "-" + id)
                    .podName(podName)
                    .serviceName(serviceName)
                    .correlationId(correlationId)
                    .requestTimestamp(requestTimestamp)
                    .method(methodOut)
                    .url(finalUrl)
                    .requestBody(requestBody)
                    .responseBody(responseBody)
                    .callStatus(status)
                    .build();
        }
    }
}
