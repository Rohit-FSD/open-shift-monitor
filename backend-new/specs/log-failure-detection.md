# Log Failure Detection — Spec

## Goal
When a service is unhealthy, scan its recent logs, classify the failures into one of
three categories, and return an actionable recommendation per failure.

## Failure categories

| Category           | What it means                                                                 | Recommendation source                                          |
|--------------------|-------------------------------------------------------------------------------|----------------------------------------------------------------|
| `APPLICATION_ERROR`| The app itself threw a known mapped error code (e.g. `700`–`706`, `CON_CSS_*`). | `error-codes.json` — resolved by code                          |
| `POD_FAILURE`      | Pod-level issue: ImagePullBackOff, CrashLoopBackOff, OOMKilled, `Error` phase. | Static: "Contact DevOps — pod health issue"                    |
| `DOWNSTREAM_FAILURE`| Log indicates a dependency (SOAP/REST/DB) returned an error.                  | Static: "Downstream dependency failed — please contact owner"  |

Evaluation order when a log line matches multiple rules:
**POD_FAILURE → APPLICATION_ERROR → DOWNSTREAM_FAILURE**
(pod-level issues are structural and dominate; app-mapped codes are more specific than generic downstream).

## API

**POST** `/api/log-failure-detection/analyze`

Request:
```json
{
  "envName": "DEV",
  "serviceName": "bcp-css-service",
  "timeRangeMinutes": 30,
  "includeSamples": true,
  "sampleLimit": 5
}
```

Response (shape):
```json
{
  "envName": "DEV",
  "serviceName": "bcp-css-service",
  "healthStatus": "DEGRADED",
  "analyzedAt": "2026-04-19T18:30:00",
  "timeRangeMinutes": 30,
  "summary": {
    "totalLogsScanned": 2048,
    "totalFailures": 37,
    "applicationErrorCount": 22,
    "podFailureCount": 2,
    "downstreamFailureCount": 13
  },
  "applicationErrors": [
    {
      "errorCode": "CON_CSS_10023",
      "errorMessage": "...",
      "endpoint": "/get-customer-detail-by-account",
      "occurrences": 12,
      "severity": "MEDIUM",
      "recommendedSolution": "Review application logs for detailed error information",
      "description": "Follow these general steps to diagnose the issue",
      "steps": ["...", "..."],
      "sampleLogs": ["..."]
    }
  ],
  "podFailures": [
    {
      "podName": "bcp-css-service-7f...",
      "reason": "CrashLoopBackOff",
      "occurrences": 5,
      "recommendedSolution": "Contact DevOps — pod is in CrashLoopBackOff",
      "sampleLogs": ["..."]
    }
  ],
  "downstreamFailures": [
    {
      "dependency": "account-service",
      "protocol": "REST",
      "sampleError": "Read timed out",
      "occurrences": 13,
      "recommendedSolution": "Downstream dependency failed — please contact owner",
      "sampleLogs": ["..."]
    }
  ]
}
```

## Classification rules

### POD_FAILURE
Derived from Kubernetes pod status, not logs. A pod contributes a `PodFailureDetail`
when any of these is true:
- `pod.status.phase` is `Failed`
- Any container status has `waiting.reason` in
  `{ImagePullBackOff, ErrImagePull, CrashLoopBackOff, CreateContainerConfigError}`
- Any container status has `terminated.reason` in `{OOMKilled, Error}`
- `restartCount > 5` within the window

### APPLICATION_ERROR
A log message matches the error-code regex from `error-codes.json`. Grouping key =
`errorCode`. The endpoint is extracted from the message (regex `endpoint=([^\s,]+)`
or nearest preceding request-path log line).

### DOWNSTREAM_FAILURE
A log message contains any of:
- `Read timed out`, `Connection refused`, `SocketTimeoutException`
- HTTP 5xx from a named downstream (`status=5\d\d`)
- `SOAPFaultException`, `ResourceAccessException`, `RestClientException`
- DB errors: `SQLException`, `JDBCConnectionException`

Grouping key = best-effort extracted dependency name (URL host or class name).

## Configuration: `error-codes.json`

Loaded from classpath at startup, lives at
`src/main/resources/error-codes/error-codes.json`:

```json
[
  {
    "code": "CON_CSS_10023",
    "pattern": "CON_CSS_10023",
    "severity": "MEDIUM",
    "recommendedSolution": "Review application logs for detailed error information",
    "description": "Follow these general steps to diagnose the issue",
    "steps": [
      "Review application logs for detailed error information",
      "Check service health and pod status",
      "Contact support team if issue persists"
    ]
  }
]
```

A special entry with `"code": "DEFAULT"` is used when no specific mapping is found.

## Health status
Derived at the end of analysis:
- `HEALTHY` — 0 failures
- `DEGRADED` — failures present but pod phase Running
- `DOWN` — any POD_FAILURE present, or zero logs were returned

## Non-goals
- Historical trends (that's success-rate)
- Root-cause inference across services (the recommendation is per-service)
- Auto-remediation
