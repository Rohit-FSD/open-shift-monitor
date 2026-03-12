# Backend: Log Failure Detection API (Fabric8 + OpenShift)

Reference implementation for the **log failure detection** endpoint using **Fabric8 Kubernetes client**. Use path variables for **environment** (namespace) and **service name**; identify pods via **deployment selector match labels**; read **ERROR-only** logs from OpenShift and **trim** to the real reason.

---

## 1. API contract

- **Endpoint:** `GET /api/log-failure-detection/{environment}/{serviceName}`
- **Path variables:** `environment` (OpenShift namespace), `serviceName` (deployment/service name).
- **Optional query:** `tailLines` (e.g. `1000`, default `2000`) to limit log lines read per pod.

**Response shape** (matches your UI/screenshots):

```json
{
  "environment": "V4-QA01",
  "timestamp": "2026-03-09T10:11:59.1027344",
  "services": [{
    "service": "bcp-css-service",
    "healthStatus": "HEALTHY",
    "failures": [...],
    "podFailures": [...],
    "applicationErrors": [...]
  }]
}
```

---

## 2. DTOs (response model)

```java
// LogFailureDetectionResponse.java
@Data
@Builder
public class LogFailureDetectionResponse {
    private String environment;
    private String timestamp;
    private List<ServiceLogStatus> services;
}

@Data
@Builder
public class ServiceLogStatus {
    private String service;
    private String healthStatus;
    private List<FailureDetail> failures;
    private List<PodFailureDetail> podFailures;
    private List<ApplicationErrorDetail> applicationErrors;
}

@Data
@Builder
public class FailureDetail {
    private String endpoint;
    private String statusCode;
    private String httpMethod;
    private String errorType;
    private String errorMessage;
    private List<String> sampleLogs;
}

@Data
@Builder
public class PodFailureDetail {
    private String podName;
    private String failureType;
    private String status;
    private String reason;
    private String message;
    private Integer restartCount;
    private String lastTransitionTime;
    private List<String> containerFailures;
}

@Data
@Builder
public class ApplicationErrorDetail {
    private String exceptionType;
    private String message;
    private List<String> sampleLogs;
}
```

---

## 3. Find pods using deployment selector match labels

Use **all** labels from `deployment.getSpec().getSelector().getMatchLabels()` (e.g. `app=<serviceName>`, `deployment=<serviceName>`) so the pod list exactly matches the deployment.

```java
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;

public List<Pod> getPodsForDeployment(KubernetesClient client, String namespace, String serviceName) {
    Deployment deployment = client.apps().deployments()
            .inNamespace(namespace)
            .withName(serviceName)
            .get();

    if (deployment == null || deployment.getSpec() == null
            || deployment.getSpec().getSelector() == null
            || deployment.getSpec().getSelector().getMatchLabels() == null) {
        return Collections.emptyList();
    }

    Map<String, String> matchLabels = deployment.getSpec().getSelector().getMatchLabels();
    return client.pods()
            .inNamespace(namespace)
            .withLabels(matchLabels)
            .list()
            .getItems();
}
```

---

## 4. Read pod logs from OpenShift (Fabric8)

Read **text logs** from each container, then filter and trim in Java (Fabric8 does not support server-side “ERROR only” filtering).

```java
import io.fabric8.kubernetes.client.dsl.PodResource;

public String getPodLogs(KubernetesClient client, String namespace, String podName, String containerName, int tailLines) {
    PodResource<Pod> podResource = client.pods()
            .inNamespace(namespace)
            .withName(podName);

    try {
        String fullLog;
        if (containerName != null && !containerName.isEmpty()) {
            fullLog = podResource.inContainer(containerName).getLog(true);
        } else {
            fullLog = podResource.getLog(true);
        }
        // Fabric8 getLog() returns full log; keep last tailLines only
        if (fullLog == null || fullLog.isEmpty()) return "";
        String[] lines = fullLog.split("\n");
        int from = Math.max(0, lines.length - tailLines);
        return String.join("\n", Arrays.copyOfRange(lines, from, lines.length));
    } catch (Exception e) {
        return "";
    }
}
```

**Tail lines:** Fabric8’s `getLog()` often returns full log. To approximate “last N lines”:

- Either use `LogWatch` and read stream, then keep last N lines in memory,  
- Or call `getLog()` and in Java do:  
  `String[] lines = log.split("\n");`  
  `int from = Math.max(0, lines.length - tailLines);`  
  `String tail = String.join("\n", Arrays.copyOfRange(lines, from, lines.length));`  
  then run **ERROR-only** and **trim** logic on `tail`.

---

## 5. Filter and trim: only ERROR logs, real reason

- **Filter:** Keep only lines that represent errors (e.g. contain `ERROR`, or `Exception`, or your app’s error level).
- **Trim:** For each such line (or contiguous block), extract the “real reason” (e.g. first sentence, or up to 500 chars, or strip stack trace noise).

Example: filter ERROR-level lines and trim to a fixed length and one line per “reason”:

```java
private static final int MAX_SAMPLE_LENGTH = 500;
private static final int MAX_SAMPLES = 20;

public List<String> filterAndTrimErrorLogs(String rawLog) {
    if (rawLog == null || rawLog.isEmpty()) return Collections.emptyList();

    List<String> samples = new ArrayList<>();
    String[] lines = rawLog.split("\n");

    for (String line : lines) {
        if (!isErrorLine(line)) continue;

        String trimmed = trimToRealReason(line);
        if (!trimmed.isEmpty() && samples.size() < MAX_SAMPLES) {
            samples.add(trimmed.length() > MAX_SAMPLE_LENGTH
                    ? trimmed.substring(0, MAX_SAMPLE_LENGTH) + "..."
                    : trimmed);
        }
    }
    return samples;
}

private boolean isErrorLine(String line) {
    String lower = line.toLowerCase();
    return lower.contains("error") || lower.contains("exception") || lower.contains("failed")
            || lower.contains("fault") || lower.contains("timeout");
}

private String trimToRealReason(String line) {
    // Drop very long base64 or JSON blobs; keep message part
    String s = line.replaceAll("\\s+", " ").trim();
    int max = 500;
    return s.length() <= max ? s : s.substring(0, max) + "...";
}
```

You can extend `isErrorLine` / `trimToRealReason` to detect **downstream (REST/SOAP/DB)** vs **application (NPE, etc.)** and map to `failures` vs `applicationErrors` (see below).

---

## 6. Classify errors: downstream vs application

Use simple pattern checks on the trimmed line (or block) to decide type:

- **Downstream REST:** e.g. `"status code: 500"`, `"Read timed out"`, `"Connection refused"`, `"INTERNAL_SERVER_ERROR"`.
- **Downstream SOAP:** e.g. `"SOAPFaultException"`, `"faultcode"`, `"BEMING Gateway Processing Failure"`.
- **Downstream DB:** e.g. `"SQLException"`, `"ORA-"`, `"PSQLException"`, `"SQLTimeoutException"`.
- **Application:** e.g. `"NullPointerException"`, `"IllegalStateException"`, `"IllegalArgumentException"`.

Then push into `FailureDetail` (with `endpoint`, `statusCode`, `httpMethod`, `errorType`, `errorMessage`, `sampleLogs`) or into `ApplicationErrorDetail` (`exceptionType`, `message`, `sampleLogs`). Use one trimmed line (or a short block) per `sampleLogs` entry.

---

## 7. Pod-level failures (ImagePullBackOff, CrashLoopBackOff, etc.)

From **pod status** (no log reading), detect infrastructure issues and fill `podFailures`:

```java
public List<PodFailureDetail> detectPodFailures(Pod pod) {
    List<PodFailureDetail> list = new ArrayList<>();
    if (pod.getStatus() == null) return list;

    for (io.fabric8.kubernetes.api.model.ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
        io.fabric8.kubernetes.api.model.ContainerState state = cs.getState();
        if (state == null) continue;

        io.fabric8.kubernetes.api.model.ContainerStateWaiting waiting = state.getWaiting();
        if (waiting != null) {
            String reason = waiting.getReason(); // e.g. ImagePullBackOff, CrashLoopBackOff
            list.add(PodFailureDetail.builder()
                    .podName(pod.getMetadata().getName())
                    .failureType(reason != null ? reason.toUpperCase() : "UNKNOWN")
                    .status("Waiting")
                    .reason(reason)
                    .message(waiting.getMessage())
                    .restartCount(cs.getRestartCount() != null ? cs.getRestartCount() : 0)
                    .containerFailures(Collections.singletonList(
                            cs.getName() + ": " + reason + " " + waiting.getMessage()))
                    .build());
        }
        // Similarly check getTerminated() for OOMKilled, Error, etc.
    }
    return list;
}
```

Add `lastTransitionTime` from `pod.getStatus().getConditions()` if you need it.

---

## 8. Service implementation sketch

```java
@Service
@RequiredArgsConstructor
public class LogFailureDetectionService {

    private final KubernetesClient kubernetesClient; // your Fabric8 client bean

    public LogFailureDetectionResponse detect(String environment, String serviceName, int tailLines) {
        List<Pod> pods = getPodsForDeployment(kubernetesClient, environment, serviceName);

        List<FailureDetail> failures = new ArrayList<>();
        List<PodFailureDetail> podFailures = new ArrayList<>();
        List<ApplicationErrorDetail> applicationErrors = new ArrayList<>();

        for (Pod pod : pods) {
            podFailures.addAll(detectPodFailures(pod));

            for (Container container : pod.getSpec().getContainers()) {
                String log = getPodLogs(kubernetesClient, environment, pod.getMetadata().getName(),
                        container.getName(), tailLines);
                List<String> errorSamples = filterAndTrimErrorLogs(log);
                // Classify and add to failures vs applicationErrors (see section 6)
            }
        }

        String healthStatus = (failures.isEmpty() && podFailures.isEmpty() && applicationErrors.isEmpty())
                ? "HEALTHY" : "CRITICAL";

        ServiceLogStatus serviceStatus = ServiceLogStatus.builder()
                .service(serviceName)
                .healthStatus(healthStatus)
                .failures(failures)
                .podFailures(podFailures)
                .applicationErrors(applicationErrors)
                .build();

        return LogFailureDetectionResponse.builder()
                .environment(environment)
                .timestamp(Instant.now().toString())
                .services(Collections.singletonList(serviceStatus))
                .build();
    }
}
```

---

## 9. Controller

```java
@RestController
@RequestMapping("/api/log-failure-detection")
@RequiredArgsConstructor
public class LogFailureDetectionController {

    private final LogFailureDetectionService logFailureDetectionService;

    @GetMapping("/{environment}/{serviceName}")
    public ResponseEntity<LogFailureDetectionResponse> getLogFailures(
            @PathVariable String environment,
            @PathVariable String serviceName,
            @RequestParam(required = false, defaultValue = "2000") int tailLines) {
        LogFailureDetectionResponse response = logFailureDetectionService.detect(environment, serviceName, tailLines);
        return ResponseEntity.ok(response);
    }
}
```

---

## 10. Summary

| Requirement | Implementation |
|-------------|----------------|
| Fabric8 client | Use existing client; list pods and read logs as above. |
| Log source | OpenShift pod logs via `PodResource#getLog()` (and optionally inContainer). |
| Only error logs | Filter in Java: keep lines matching ERROR / Exception / failed / fault / timeout; trim to real reason (length limit, single line or short block). |
| Sample logs trimmed | One short string per entry in `sampleLogs` (e.g. max 500 chars, max 20 entries). |
| Identify pods | `deployment.getSpec().getSelector().getMatchLabels()` → `pods().inNamespace(ns).withLabels(matchLabels).list()`. |
| Path variables | `environment` (namespace), `serviceName` (deployment name). |

If you share your existing Fabric8 client configuration (e.g. how you connect per environment), the same pattern can be used to select the right client for `environment` and then run the steps above.
