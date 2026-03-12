package com.yourorg.monitoring.service;

import com.yourorg.monitoring.dto.ApplicationErrorDetail;
import com.yourorg.monitoring.dto.FailureDetail;
import com.yourorg.monitoring.dto.LogFailureDetectionResponse;
import com.yourorg.monitoring.dto.PodFailureDetail;
import com.yourorg.monitoring.dto.ServiceLogStatus;
import com.yourorg.monitoring.openshift.EnvironmentClientProvider;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStateWaiting;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LogFailureDetectionService {

  private final EnvironmentClientProvider envProvider;

  private static final int MAX_SAMPLES_PER_CONTAINER = 30;
  private static final int MAX_LINE_LEN = 800;

  private static final Pattern EXCEPTION_TYPE =
      Pattern.compile("(NullPointerException|IllegalStateException|IllegalArgumentException|IndexOutOfBoundsException|RuntimeException|Exception)");

  public LogFailureDetectionResponse detect(String environment, String serviceName, int tailLines) {
    KubernetesClient client = envProvider.getClient(environment);
    String namespace = envProvider.getNamespace(environment);

    List<Pod> pods = getPodsForDeploymentSelector(client, namespace, serviceName);

    List<FailureDetail> failures = new ArrayList<>();
    List<PodFailureDetail> podFailures = new ArrayList<>();
    List<ApplicationErrorDetail> applicationErrors = new ArrayList<>();

    for (Pod pod : pods) {
      podFailures.addAll(detectPodFailures(pod));

      if (pod.getSpec() == null || pod.getSpec().getContainers() == null) continue;

      for (Container container : pod.getSpec().getContainers()) {
        String log = getPodLog(client, namespace, pod.getMetadata().getName(), container.getName());
        String tail = getLastLines(log, tailLines);
        List<String> errorLines = extractErrorLines(tail, MAX_SAMPLES_PER_CONTAINER);

        for (String line : errorLines) {
          String trimmed = normalizeAndTrim(line);
          Optional<String> exType = extractExceptionType(trimmed);

          if (exType.isPresent()) {
            applicationErrors.add(ApplicationErrorDetail.builder()
                .exceptionType(exType.get())
                .message(trimmed)
                .sampleLogs(Collections.singletonList(trimmed))
                .build());
          } else {
            failures.add(FailureDetail.builder()
                .endpoint("Unknown endpoint")
                .statusCode("")
                .httpMethod("")
                .errorType(classifyDownstreamType(trimmed))
                .errorMessage(trimmed)
                .sampleLogs(Collections.singletonList(trimmed))
                .build());
          }
        }
      }
    }

    failures = dedupeFailures(failures);
    applicationErrors = dedupeAppErrors(applicationErrors);
    podFailures = dedupePodFailures(podFailures);

    String healthStatus = (failures.isEmpty() && applicationErrors.isEmpty() && podFailures.isEmpty())
        ? "HEALTHY"
        : "CRITICAL";

    ServiceLogStatus svc = ServiceLogStatus.builder()
        .service(serviceName)
        .healthStatus(healthStatus)
        .failures(failures)
        .podFailures(podFailures)
        .applicationErrors(applicationErrors)
        .build();

    return LogFailureDetectionResponse.builder()
        .environment(environment)
        .timestamp(Instant.now().toString())
        .services(Collections.singletonList(svc))
        .build();
  }

  private List<Pod> getPodsForDeploymentSelector(KubernetesClient client, String namespace, String serviceName) {
    Deployment deployment = client.apps().deployments().inNamespace(namespace).withName(serviceName).get();
    if (deployment == null || deployment.getSpec() == null || deployment.getSpec().getSelector() == null) {
      return Collections.emptyList();
    }

    Map<String, String> matchLabels = deployment.getSpec().getSelector().getMatchLabels();
    if (matchLabels == null || matchLabels.isEmpty()) {
      return Collections.emptyList();
    }

    PodList list = client.pods().inNamespace(namespace).withLabels(matchLabels).list();
    return list != null && list.getItems() != null ? list.getItems() : Collections.emptyList();
  }

  private String getPodLog(KubernetesClient client, String namespace, String podName, String containerName) {
    try {
      return client.pods()
          .inNamespace(namespace)
          .withName(podName)
          .inContainer(containerName)
          .getLog(true);
    } catch (Exception e) {
      return "";
    }
  }

  private String getLastLines(String log, int tailLines) {
    if (log == null || log.isBlank()) return "";
    if (tailLines <= 0) return log;

    String[] lines = log.split("\n");
    int from = Math.max(0, lines.length - tailLines);
    return String.join("\n", Arrays.copyOfRange(lines, from, lines.length));
  }

  private List<String> extractErrorLines(String log, int max) {
    if (log == null || log.isBlank()) return Collections.emptyList();
    List<String> out = new ArrayList<>();
    for (String line : log.split("\n")) {
      if (isErrorLike(line)) {
        out.add(line);
        if (out.size() >= max) break;
      }
    }
    return out;
  }

  private boolean isErrorLike(String line) {
    if (line == null) return false;
    String s = line.toLowerCase(Locale.ROOT);
    return s.contains(" error ")
        || s.contains("error:")
        || s.contains("exception")
        || s.contains("failed")
        || s.contains("fault")
        || s.contains("timeout")
        || s.contains("connection refused")
        || s.contains("read timed out")
        || s.contains("internal server error")
        || s.contains("status=500")
        || s.contains("status code: 500");
  }

  private String normalizeAndTrim(String line) {
    String s = line == null ? "" : line.replaceAll("\\s+", " ").trim();
    if (s.length() > MAX_LINE_LEN) return s.substring(0, MAX_LINE_LEN) + "...";
    return s;
  }

  private Optional<String> extractExceptionType(String line) {
    if (line == null) return Optional.empty();
    var m = EXCEPTION_TYPE.matcher(line);
    return m.find() ? Optional.of(m.group(1)) : Optional.empty();
  }

  private String classifyDownstreamType(String line) {
    String s = line == null ? "" : line.toLowerCase(Locale.ROOT);
    if (s.contains("soap") || s.contains("soapfaultexception") || s.contains("faultcode") || s.contains("faultstring")) {
      return "DOWNSTREAM_SOAP";
    }
    if (s.contains("sql") || s.contains("ora-") || s.contains("psqlexception") || s.contains("sqltimeoutexception")) {
      return "DOWNSTREAM_DB";
    }
    if (s.contains("http") || s.contains("status") || s.contains("internal server error") || s.contains("read timed out") || s.contains("connection refused")) {
      return "DOWNSTREAM_REST";
    }
    return "INTERNAL_SERVER_ERROR";
  }

  private List<PodFailureDetail> detectPodFailures(Pod pod) {
    if (pod == null || pod.getMetadata() == null || pod.getStatus() == null) return Collections.emptyList();
    List<ContainerStatus> statuses = pod.getStatus().getContainerStatuses();
    if (statuses == null) return Collections.emptyList();

    List<PodFailureDetail> out = new ArrayList<>();

    for (ContainerStatus cs : statuses) {
      if (cs == null || cs.getState() == null) continue;

      ContainerStateWaiting waiting = cs.getState().getWaiting();
      if (waiting != null) {
        String reason = waiting.getReason();
        if (reason != null && (reason.contains("BackOff") || reason.contains("CrashLoop") || reason.contains("ImagePull"))) {
          out.add(PodFailureDetail.builder()
              .podName(pod.getMetadata().getName())
              .failureType(reason.toUpperCase(Locale.ROOT))
              .status("Waiting")
              .reason(reason)
              .message(waiting.getMessage())
              .restartCount(cs.getRestartCount() != null ? cs.getRestartCount() : 0)
              .containerFailures(Collections.singletonList(cs.getName() + ": " + reason + " " + safe(waiting.getMessage())))
              .build());
        }
      }

      ContainerStateTerminated term = cs.getState().getTerminated();
      if (term != null) {
        String reason = term.getReason();
        if (reason != null && (reason.equalsIgnoreCase("OOMKilled") || reason.equalsIgnoreCase("Error"))) {
          out.add(PodFailureDetail.builder()
              .podName(pod.getMetadata().getName())
              .failureType(reason.toUpperCase(Locale.ROOT))
              .status("Terminated")
              .reason(reason)
              .message(term.getMessage())
              .restartCount(cs.getRestartCount() != null ? cs.getRestartCount() : 0)
              .containerFailures(Collections.singletonList(cs.getName() + ": " + reason + " " + safe(term.getMessage())))
              .build());
        }
      }
    }

    return out;
  }

  private String safe(String s) {
    return s == null ? "" : s;
  }

  private List<FailureDetail> dedupeFailures(List<FailureDetail> in) {
    Map<String, FailureDetail> map = new LinkedHashMap<>();
    for (FailureDetail f : in) {
      String key = f.getErrorType() + "|" + f.getErrorMessage();
      map.putIfAbsent(key, f);
    }
    return new ArrayList<>(map.values());
  }

  private List<ApplicationErrorDetail> dedupeAppErrors(List<ApplicationErrorDetail> in) {
    Map<String, ApplicationErrorDetail> map = new LinkedHashMap<>();
    for (ApplicationErrorDetail a : in) {
      String key = a.getExceptionType() + "|" + a.getMessage();
      map.putIfAbsent(key, a);
    }
    return new ArrayList<>(map.values());
  }

  private List<PodFailureDetail> dedupePodFailures(List<PodFailureDetail> in) {
    Map<String, PodFailureDetail> map = new LinkedHashMap<>();
    for (PodFailureDetail p : in) {
      String key = p.getPodName() + "|" + p.getFailureType() + "|" + p.getReason();
      map.putIfAbsent(key, p);
    }
    return new ArrayList<>(map.values());
  }
}

