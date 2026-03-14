// package com.yourorg.monitoring.service;

// import com.yourorg.monitoring.dto.ApplicationErrorDetail;
// import com.yourorg.monitoring.dto.FailureDetail;
// import com.yourorg.monitoring.dto.LogFailureDetectionResponse;
// import com.yourorg.monitoring.dto.PodFailureDetail;
// import com.yourorg.monitoring.dto.ServiceLogStatus;
// import com.yourorg.monitoring.openshift.EnvironmentClientProvider;
// import com.yourorg.monitoring.service.rules.FailureCategory;
// import com.yourorg.monitoring.service.rules.FailureRuleEngine;
// import com.yourorg.monitoring.service.rules.LogBlockExtractor;
// import com.yourorg.monitoring.service.rules.LogFailureProperties;
// import com.yourorg.monitoring.service.rules.MatchedFailure;
// import io.fabric8.kubernetes.api.model.Container;
// import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
// import io.fabric8.kubernetes.api.model.ContainerStateWaiting;
// import io.fabric8.kubernetes.api.model.ContainerStatus;
// import io.fabric8.kubernetes.api.model.Pod;
// import io.fabric8.kubernetes.api.model.PodList;
// import io.fabric8.kubernetes.api.model.apps.Deployment;
// import io.fabric8.kubernetes.client.KubernetesClient;
// import lombok.RequiredArgsConstructor;
// import org.springframework.stereotype.Service;

// import java.io.FileWriter;
// import java.io.IOException;
// import java.time.Instant;
// import java.util.ArrayList;
// import java.util.Arrays;
// import java.util.Collections;
// import java.util.LinkedHashMap;
// import java.util.List;
// import java.util.Locale;
// import java.util.Map;
// import java.util.Optional;

// @Service
// @RequiredArgsConstructor
// public class LogFailureDetectionService {

//   private final EnvironmentClientProvider envProvider;
//   private final LogFailureProperties props;
//   private final FailureRuleEngine ruleEngine;
//   private final LogBlockExtractor blockExtractor;

//   public LogFailureDetectionResponse detect(String environment, String serviceName, int tailLines) {
//     KubernetesClient client = envProvider.getClient(environment);
//     String namespace = envProvider.getNamespace(environment);

//     List<Pod> pods = getPodsForDeploymentSelector(client, namespace, serviceName);

//     List<FailureDetail> failures = new ArrayList<>();
//     List<PodFailureDetail> podFailures = new ArrayList<>();
//     List<ApplicationErrorDetail> applicationErrors = new ArrayList<>();

//     for (Pod pod : pods) {
//       podFailures.addAll(detectPodFailures(pod));

//       if (pod.getSpec() == null || pod.getSpec().getContainers() == null) continue;

//       for (Container container : pod.getSpec().getContainers()) {
//         String log = getPodLog(client, namespace, pod.getMetadata().getName(), container.getName());
//         String tail = getLastLines(log, tailLines > 0 ? tailLines : props.getTailLines());

//         List<String> blocks = blockExtractor.extractErrorBlocks(
//             tail,
//             props.getMaxSamplesPerContainer(),
//             props.getMaxBlockLines()
//         );

//         for (String block : blocks) {
//           String message = normalizeAndTrim(block, props.getMaxLineLen());
//           Optional<MatchedFailure> match = ruleEngine.match(message);

//           if (match.isPresent() && match.get().getCategory() == FailureCategory.APPLICATION) {
//             String exType = !isBlank(match.get().getExceptionType())
//                 ? match.get().getExceptionType()
//                 : match.get().getErrorType();
//             applicationErrors.add(ApplicationErrorDetail.builder()
//                 .exceptionType(exType)
//                 .message(message)
//                 .sampleLogs(Collections.singletonList(message))
//                 .build());
//           } else {
//             MatchedFailure mf = match.orElse(MatchedFailure.builder()
//                 .category(FailureCategory.UNKNOWN)
//                 .errorType("UNKNOWN")
//                 .message(message)
//                 .build());

//             failures.add(FailureDetail.builder()
//                 .endpoint(isBlank(mf.getEndpoint()) ? "Unknown endpoint" : mf.getEndpoint())
//                 .statusCode(isBlank(mf.getStatusCode()) ? "" : mf.getStatusCode())
//                 .httpMethod(isBlank(mf.getHttpMethod()) ? "" : mf.getHttpMethod())
//                 .errorType(isBlank(mf.getErrorType()) ? "UNKNOWN" : mf.getErrorType())
//                 .errorMessage(message)
//                 .sampleLogs(Collections.singletonList(message))
//                 .build());
//           }
//         }
//       }
//     }

//     failures = dedupeFailures(failures);
//     applicationErrors = dedupeAppErrors(applicationErrors);
//     podFailures = dedupePodFailures(podFailures);

//     String healthStatus = (failures.isEmpty() && applicationErrors.isEmpty() && podFailures.isEmpty())
//         ? "HEALTHY"
//         : "CRITICAL";

//     ServiceLogStatus svc = ServiceLogStatus.builder()
//         .service(serviceName)
//         .healthStatus(healthStatus)
//         .failures(failures)
//         .podFailures(podFailures)
//         .applicationErrors(applicationErrors)
//         .build();

//     // #region agent log
//     if (!failures.isEmpty() || !applicationErrors.isEmpty() || !podFailures.isEmpty()) {
//       String firstEndpoint = failures.isEmpty() ? "" : failures.get(0).getEndpoint();
//       agentLog(
//           "H2",
//           "detect() summary",
//           "environment=\"" + safe(environment)
//               + "\",serviceName=\"" + safe(serviceName)
//               + "\",healthStatus=\"" + healthStatus
//               + "\",failuresCount=" + failures.size()
//               + ",applicationErrorsCount=" + applicationErrors.size()
//               + ",podFailuresCount=" + podFailures.size()
//               + ",firstEndpoint=\"" + safe(firstEndpoint) + "\""
//       );
//     }
//     // #endregion

//     return LogFailureDetectionResponse.builder()
//         .environment(environment)
//         .timestamp(Instant.now().toString())
//         .services(Collections.singletonList(svc))
//         .build();
//   }

//   private List<Pod> getPodsForDeploymentSelector(KubernetesClient client, String namespace, String serviceName) {
//     Deployment deployment = client.apps().deployments().inNamespace(namespace).withName(serviceName).get();
//     if (deployment == null || deployment.getSpec() == null || deployment.getSpec().getSelector() == null) {
//       return Collections.emptyList();
//     }

//     Map<String, String> matchLabels = deployment.getSpec().getSelector().getMatchLabels();
//     if (matchLabels == null || matchLabels.isEmpty()) {
//       return Collections.emptyList();
//     }

//     PodList list = client.pods().inNamespace(namespace).withLabels(matchLabels).list();
//     return list != null && list.getItems() != null ? list.getItems() : Collections.emptyList();
//   }

//   private String getPodLog(KubernetesClient client, String namespace, String podName, String containerName) {
//     try {
//       StringBuilder out = new StringBuilder();

//       // Your earlier implementation checked "previous/terminated" logs first (often where the crash stacktrace is).
//       // Fabric8 exposes this via `.terminated()`.
//       try {
//         String previous = client.pods()
//             .inNamespace(namespace)
//             .withName(podName)
//             .inContainer(containerName)
//             .terminated()
//             .getLog();
//         if (previous != null && !previous.isBlank()) {
//           out.append(previous).append("\n");
//         }
//       } catch (Exception ignored) {
//         // best-effort; fall back to current logs
//       }

//       String current = client.pods()
//           .inNamespace(namespace)
//           .withName(podName)
//           .inContainer(containerName)
//           .getLog(true);
//       if (current != null && !current.isBlank()) {
//         out.append(current);
//       }

//       return out.toString();
//     } catch (Exception e) {
//       return "";
//     }
//   }

//   private String getLastLines(String log, int tailLines) {
//     if (log == null || log.isBlank()) return "";
//     if (tailLines <= 0) return log;

//     String[] lines = log.split("\n");
//     int from = Math.max(0, lines.length - tailLines);
//     return String.join("\n", Arrays.copyOfRange(lines, from, lines.length));
//   }

//   private String normalizeAndTrim(String text, int maxLen) {
//     if (text == null) return "";
//     String s = text.replaceAll("\\s+", " ").trim();
//     if (maxLen > 0 && s.length() > maxLen) return s.substring(0, maxLen) + "...";
//     return s;
//   }

//   private boolean isBlank(String s) {
//     return s == null || s.isBlank();
//   }

//   private List<PodFailureDetail> detectPodFailures(Pod pod) {
//     if (pod == null || pod.getMetadata() == null || pod.getStatus() == null) return Collections.emptyList();
//     List<ContainerStatus> statuses = pod.getStatus().getContainerStatuses();
//     if (statuses == null) return Collections.emptyList();

//     List<PodFailureDetail> out = new ArrayList<>();

//     for (ContainerStatus cs : statuses) {
//       if (cs == null || cs.getState() == null) continue;

//       ContainerStateWaiting waiting = cs.getState().getWaiting();
//       if (waiting != null) {
//         String reason = waiting.getReason();
//         if (reason != null && (reason.contains("BackOff") || reason.contains("CrashLoop") || reason.contains("ImagePull"))) {
//           out.add(PodFailureDetail.builder()
//               .podName(pod.getMetadata().getName())
//               .failureType(reason.toUpperCase(Locale.ROOT))
//               .status("Waiting")
//               .reason(reason)
//               .message(waiting.getMessage())
//               .restartCount(cs.getRestartCount() != null ? cs.getRestartCount() : 0)
//               .containerFailures(Collections.singletonList(cs.getName() + ": " + reason + " " + safe(waiting.getMessage())))
//               .build());
//         }
//       }

//       ContainerStateTerminated term = cs.getState().getTerminated();
//       if (term != null) {
//         String reason = term.getReason();
//         if (reason != null && (reason.equalsIgnoreCase("OOMKilled") || reason.equalsIgnoreCase("Error"))) {
//           out.add(PodFailureDetail.builder()
//               .podName(pod.getMetadata().getName())
//               .failureType(reason.toUpperCase(Locale.ROOT))
//               .status("Terminated")
//               .reason(reason)
//               .message(term.getMessage())
//               .restartCount(cs.getRestartCount() != null ? cs.getRestartCount() : 0)
//               .containerFailures(Collections.singletonList(cs.getName() + ": " + reason + " " + safe(term.getMessage())))
//               .build());
//         }
//       }
//     }

//     return out;
//   }

//   private String safe(String s) {
//     return s == null ? "" : s;
//   }

//   private List<FailureDetail> dedupeFailures(List<FailureDetail> in) {
//     Map<String, FailureDetail> map = new LinkedHashMap<>();
//     for (FailureDetail f : in) {
//       String key = f.getErrorType() + "|" + f.getErrorMessage();
//       map.putIfAbsent(key, f);
//     }
//     return new ArrayList<>(map.values());
//   }

//   private List<ApplicationErrorDetail> dedupeAppErrors(List<ApplicationErrorDetail> in) {
//     Map<String, ApplicationErrorDetail> map = new LinkedHashMap<>();
//     for (ApplicationErrorDetail a : in) {
//       String key = a.getExceptionType() + "|" + a.getMessage();
//       map.putIfAbsent(key, a);
//     }
//     return new ArrayList<>(map.values());
//   }

//   private List<PodFailureDetail> dedupePodFailures(List<PodFailureDetail> in) {
//     Map<String, PodFailureDetail> map = new LinkedHashMap<>();
//     for (PodFailureDetail p : in) {
//       String key = p.getPodName() + "|" + p.getFailureType() + "|" + p.getReason();
//       map.putIfAbsent(key, p);
//     }
//     return new ArrayList<>(map.values());
//   }

//   private String safe(String s) {
//     return s == null ? "" : s.replace("\"", "\\\"");
//   }

//   private void agentLog(String hypothesisId, String message, String dataFragment) {
//     long ts = System.currentTimeMillis();
//     String json =
//         "{\"sessionId\":\"1d8923\",\"runId\":\"run1\",\"hypothesisId\":\""
//             + hypothesisId
//             + "\",\"location\":\"LogFailureDetectionService.java:detect\",\"message\":\""
//             + message.replace("\"", "\\\"")
//             + "\",\"data\":{"
//             + dataFragment
//             + "},\"timestamp\":"
//             + ts
//             + "}\n";
//     try (FileWriter fw = new FileWriter("/Users/rohit/Downloads/open-shift-monitor-ui/.cursor/debug-1d8923.log", true)) {
//       fw.write(json);
//     } catch (IOException ignored) {
//     }
//   }
// }

