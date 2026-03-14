@Service
public class LogFailureDetectionService {

    @Autowired
    private KubernetesClient kubernetesClient;

    public ServiceFailure analyzeServiceLogs(String namespace, String serviceName) {

        List<EndpointFailure> failures = new ArrayList<>();
        List<ApplicationErrorFailure> applicationErrors = new ArrayList<>();
        List<PodFailure> podFailures = new ArrayList<>();

        List<Pod> pods = findPodsForService(namespace, serviceName);

        for (Pod pod : pods) {

            Optional<PodFailure> infraFailure = analyzePodStatus(pod);

            if (infraFailure.isPresent()) {
                podFailures.add(infraFailure.get());
                continue;
            }

            String logs = fetchContainerLogs(namespace, pod.getMetadata().getName());

            analyzeLogs(logs, failures, applicationErrors);
        }

        return ServiceFailure.builder()
                .serviceName(serviceName)
                .failures(failures)
                .podFailures(podFailures)
                .applicationErrors(applicationErrors)
                .healthStatus(determineHealthStatus(failures, podFailures))
                .build();
    }
}

private List<String> splitIntoLogBlocks(String logs) {

    List<String> blocks = new ArrayList<>();

    StringBuilder current = new StringBuilder();

    for (String line : logs.split("\n")) {

        if (line.contains("ERROR") || line.contains("Exception")) {

            if (current.length() > 0) {
                blocks.add(current.toString());
                current = new StringBuilder();
            }
        }

        current.append(line).append("\n");
    }

    if (current.length() > 0) {
        blocks.add(current.toString());
    }

    return blocks;
}
private FailureCategory detectFailureCategory(String logBlock) {

    if (containsPodFailure(logBlock)) {
        return FailureCategory.POD_FAILURE;
    }

    if (containsHttpFailure(logBlock)) {
        return FailureCategory.HTTP_FAILURE;
    }

    if (containsDatabaseFailure(logBlock)) {
        return FailureCategory.DATABASE_FAILURE;
    }

    if (containsDownstreamFailure(logBlock)) {
        return FailureCategory.DOWNSTREAM_FAILURE;
    }

    if (containsApplicationException(logBlock)) {
        return FailureCategory.APPLICATION_EXCEPTION;
    }

    return FailureCategory.UNKNOWN;
}

private boolean containsHttpFailure(String logs) {

    Pattern pattern = Pattern.compile("\\b(4\\d{2}|5\\d{2})\\b");

    return pattern.matcher(logs).find();
}

private boolean containsPodFailure(String logs) {

    Pattern pattern = Pattern.compile(
            "(CrashLoopBackOff|ImagePullBackOff|ErrImagePull|OOMKilled|ContainerCannotRun|Back-off restarting failed container)",
            Pattern.CASE_INSENSITIVE);

    return pattern.matcher(logs).find();
}

private boolean containsDownstreamFailure(String logs) {

    Pattern pattern = Pattern.compile(
            "(https?://)|(\\w+(Client|RestClient|WsClient|ServiceClient))",
            Pattern.CASE_INSENSITIVE);

    return pattern.matcher(logs).find();
}

private boolean containsDatabaseFailure(String logs) {

    Pattern pattern = Pattern.compile(
            "(SQLException|SQLSyntaxErrorException|JDBC|Connection pool exhausted|Deadlock)",
            Pattern.CASE_INSENSITIVE);

    return pattern.matcher(logs).find();
}

private boolean containsApplicationException(String logs) {

    Pattern pattern = Pattern.compile("\\w+Exception");

    return pattern.matcher(logs).find();
}

private EndpointFailure buildFailure(String block, FailureCategory category) {

    String reason = extractFailureReason(block);

    List<String> logs = extractImportantLogs(block);

    return EndpointFailure.builder()
            .errorType(category.name())
            .errorMessage(reason)
            .sampleLogs(logs)
            .build();
}

private String extractFailureReason(String logBlock) {

    return Arrays.stream(logBlock.split("\n"))
            .filter(line ->
                    line.contains("ERROR")
                            || line.contains("Exception")
                            || line.contains("Failure")
                            || line.contains("timeout")
                            || line.contains("refused"))
            .findFirst()
            .orElse("Unknown failure");
}

private List<String> extractImportantLogs(String logBlock) {

    return Arrays.stream(logBlock.split("\n"))
            .filter(line ->
                    line.contains("ERROR")
                            || line.contains("Exception")
                            || line.contains("Failure"))
            .limit(5)
            .collect(Collectors.toList());
}
private void analyzeLogs(String logs,
                         List<EndpointFailure> failures,
                         List<ApplicationErrorFailure> applicationErrors) {

    List<String> blocks = splitIntoLogBlocks(logs);

    for (String block : blocks) {

        FailureCategory category = detectFailureCategory(block);

        switch (category) {

            case POD_FAILURE:
                break;

            case HTTP_FAILURE:
            case DOWNSTREAM_FAILURE:
            case DATABASE_FAILURE:

                failures.add(buildEndpointFailure(block, category));
                break;

            case APPLICATION_EXCEPTION:

                applicationErrors.add(buildApplicationError(block));
                break;

            default:
                break;
        }
    }
}

private EndpointFailure buildEndpointFailure(String block, FailureCategory category) {

    return EndpointFailure.builder()
            .errorType(category.name())
            .errorMessage(extractFailureReason(block))
            .sampleLogs(extractImportantLogs(block))
            .build();
}

private ApplicationErrorFailure buildApplicationError(String block) {

    return ApplicationErrorFailure.builder()
            .errorMessage(extractFailureReason(block))
            .sampleLogs(extractImportantLogs(block))
            .occurrences(1)
            .build();
}
private boolean hasRecentErrors(String logs) {

    String[] lines = logs.split("\n");

    int start = Math.max(0, lines.length - 20);

    for (int i = start; i < lines.length; i++) {

        if (lines[i].contains("ERROR") ||
            lines[i].contains("Exception")) {

            return true;
        }
    }

    return false;
}

private List<EndpointFailure> extractEndpointFailures(String logs) {

    Map<String, EndpointFailure> failureMap = new HashMap<>();

    if (logs == null || logs.isEmpty()) {
        log.warn("⚠ No logs provided to analyze");
        return new ArrayList<>();
    }

    String recentLogs = filterRecentLogs(logs, 1440);
    List<String> logBlocks = splitIntoLogBlocks(recentLogs);

    log.info("Analyzing {} log blocks for root causes", logBlocks.size());

    int infrastructureCount = 0;
    int downstreamCount = 0;
    int databaseCount = 0;
    int applicationCount = 0;
    int frameworkCount = 0;
    int totalAnalyzed = 0;

    for (String block : logBlocks) {

        totalAnalyzed++;

        RootCauseAnalysis analysis = analyzeRootCause(block);

        if (analysis == null) {
            log.debug("Block {} produced no analysis", totalAnalyzed);
            continue;
        }

        switch (analysis.getCategory()) {
            case INFRASTRUCTURE -> infrastructureCount++;
            case DOWNSTREAM_DEPENDENCY -> downstreamCount++;
            case DATABASE -> databaseCount++;
            case APPLICATION -> applicationCount++;
            case FRAMEWORK -> frameworkCount++;
        }

        /*
         Skip pure framework errors that have no useful stack trace
         */
        if (analysis.getCategory() == FailureCategory.FRAMEWORK &&
                (analysis.getRelevantStackTrace() == null ||
                 analysis.getRelevantStackTrace().isEmpty())) {

            log.debug("Skipping pure framework error with no application stack trace");
            continue;
        }

        /*
         Build endpoint safely
         */
        String endpoint = null;

        if (analysis.getEndpoint() != null && !analysis.getEndpoint().isBlank()) {
            endpoint = analysis.getEndpoint();
        } else if (analysis.getDownstreamService() != null &&
                !analysis.getDownstreamService().equalsIgnoreCase("unknown")) {

            endpoint = "/api/" + camelToKebab(analysis.getDownstreamService());
        }

        /*
         Unique failure key
         */
        String key = analysis.getCategory() + ":" + endpoint + ":" + analysis.getRootException();

        EndpointFailure existing = failureMap.get(key);

        if (existing == null) {

            List<String> sampleLogs = new ArrayList<>();
            sampleLogs.add(analysis.getRootException() + ": " + analysis.getRootMessage());

            if (analysis.getRelevantStackTrace() != null) {
                sampleLogs.addAll(
                        analysis.getRelevantStackTrace()
                                .stream()
                                .limit(3)
                                .toList()
                );
            }

            String statusCode = analysis.getStatusCode() != null
                    ? analysis.getStatusCode()
                    : (analysis.getCategory() == FailureCategory.DOWNSTREAM_DEPENDENCY ? "502" : "500");

            String method = analysis.getHttpMethod() != null
                    ? analysis.getHttpMethod()
                    : "POST";

            EndpointFailure newFailure = createEndpointFailure(
                    endpoint,
                    method,
                    statusCode,
                    analysis.getCategory().getDescription(),
                    buildMeaningfulErrorMessage(analysis),
                    sampleLogs
            );

            failureMap.put(key, newFailure);

            log.debug("Added new failure {} -> {}", key, analysis.getRootMessage());

        } else {

            existing.getSampleLogs().add(
                    analysis.getRootException() + ": " + analysis.getRootMessage()
            );
        }
    }

    log.info(
            "Failure Classification: {} Infrastructure, {} Downstream, {} Database, {} Application, {} Framework",
            infrastructureCount,
            downstreamCount,
            databaseCount,
            applicationCount,
            frameworkCount
    );

    log.info(
            "Found {} unique root cause failures from {} blocks analyzed",
            failureMap.size(),
            totalAnalyzed
    );

    /*
     Fallback detection if root cause logic found nothing
     */
    if (failureMap.isEmpty() && logs.contains("ERROR")) {

        log.warn("No failures found via root cause analysis, trying fallback detection");

        String[] lines = logs.split("\n");
        int errorCount = 0;

        for (String line : lines) {

            if ((line.contains("ERROR") || line.contains("Exception") || line.contains("Failure"))
                    && !line.trim().isEmpty()) {

                List<String> sampleLogs = new ArrayList<>();
                sampleLogs.add(line);

                EndpointFailure fallbackFailure = createEndpointFailure(
                        null,
                        "POST",
                        "500",
                        "UNCLASSIFIED_ERROR",
                        line.length() > 200 ? line.substring(0, 200) : line,
                        sampleLogs
                );

                failureMap.put("fallback:" + errorCount, fallbackFailure);

                errorCount++;

                if (errorCount >= 5) break;
            }
        }

        log.info("Added {} fallback errors", errorCount);
    }

    return new ArrayList<>(failureMap.values());
}

private RootCauseAnalysis analyzeRootCause(String logBlock) {

    if (logBlock == null || logBlock.trim().isEmpty()) {
        return null;
    }

    String[] lines = logBlock.split("\n");

    List<String> exceptionBlocks = new ArrayList<>();
    StringBuilder currentBlock = new StringBuilder();

    boolean foundAnyException = false;

    for (String line : lines) {

        String trimmed = line.trim();

        if (trimmed.isEmpty()) {
            continue;
        }

        Matcher exceptionMatcher = STACK_TRACE_EXCEPTION_PATTERN.matcher(trimmed);

        if (exceptionMatcher.matches()) {

            foundAnyException = true;

            if (currentBlock.length() > 0) {
                exceptionBlocks.add(currentBlock.toString());
            }

            currentBlock = new StringBuilder(line).append("\n");

        } else if (trimmed.startsWith("at ") && currentBlock.length() > 0) {

            currentBlock.append(line).append("\n");

        } else if (currentBlock.length() > 0) {

            exceptionBlocks.add(currentBlock.toString());
            currentBlock = new StringBuilder();
        }
    }

    if (currentBlock.length() > 0) {
        exceptionBlocks.add(currentBlock.toString());
    }

    log.debug("Found {} exception blocks", exceptionBlocks.size());

    for (String block : exceptionBlocks) {

        RootCauseAnalysis analysis = analyzeExceptionBlock(block);

        if (analysis != null) {

            log.debug("Analyzed exception {} Category {}", 
                analysis.getRootException(), 
                analysis.getCategory());

            if (analysis.getCategory() != FailureCategory.FRAMEWORK ||
                    (analysis.getRelevantStackTrace() != null &&
                     !analysis.getRelevantStackTrace().isEmpty())) {

                return analysis;
            }
        }
    }

    /*
     PIPE delimited logs fallback
     */

    for (String line : lines) {

        if (line.contains("|ERROR|")) {

            RootCauseAnalysis analysis = analyzePipeDelimitedError(line);

            if (analysis != null) {
                log.debug("Found pipe-delimited error {}", analysis.getRootMessage());
                return analysis;
            }
        }
    }

    /*
     Generic fallback
     */

    for (String line : lines) {

        if (line.toUpperCase().contains("ERROR") && !line.trim().isEmpty()) {

            return RootCauseAnalysis.builder()
                    .rootException("GenericError")
                    .rootMessage(line.length() > 200 ? line.substring(0, 200) : line)
                    .category(FailureCategory.UNKNOWN)
                    .downstreamService("Unknown")
                    .detectedAt("Unknown")
                    .statusCode("500")
                    .httpMethod("POST")
                    .endpoint(null)
                    .build();
        }
    }

    return null;
}

private RootCauseAnalysis analyzeExceptionBlock(String exceptionBlock) {

    String[] lines = exceptionBlock.split("\n");

    if (lines.length == 0) {
        return null;
    }

    String firstLine = lines[0].trim();

    String exceptionClass = "Unknown";
    String exceptionMessage = firstLine;

    if (firstLine.contains(":")) {

        int colonIndex = firstLine.indexOf(":");

        exceptionClass = firstLine.substring(0, colonIndex).trim();
        exceptionMessage = firstLine.substring(colonIndex + 1).trim();

        if (exceptionClass.contains(".")) {

            String[] parts = exceptionClass.split("\\.");
            exceptionClass = parts[parts.length - 1];
        }
    }

    log.info("Exception {} Message {}", exceptionClass, exceptionMessage);

    FailureCategory category = FailureCategory.APPLICATION;

    String downstreamService = "Unknown";

    /*
     SOAP Fault detection
     */

    if (exceptionClass.contains("_faultMsg") || exceptionClass.contains("Fault")) {

        category = FailureCategory.DOWNSTREAM_DEPENDENCY;

        downstreamService = exceptionClass
                .replace("_faultMsg", "")
                .replace("Fault", "")
                .replaceAll("([a-z])([A-Z])", "$1 $2");

        Pattern clientPattern = Pattern.compile("(\\w+(Client|WsClient|ServiceClient|RestClient))");

        Matcher clientMatcher = clientPattern.matcher(exceptionBlock);

        if (clientMatcher.find()) {
            downstreamService = clientMatcher.group(1);
        }

        log.info("Detected SOAP fault for service {}", downstreamService);
    }

    /*
     Gateway / timeout detection
     */

    else if (exceptionMessage.toLowerCase().contains("gateway") ||
            exceptionMessage.toLowerCase().contains("service") ||
            exceptionMessage.toLowerCase().contains("timeout") ||
            exceptionMessage.toLowerCase().contains("connection")) {

        category = FailureCategory.DOWNSTREAM_DEPENDENCY;
        downstreamService = "External Service";
    }

    /*
     Extract stacktrace
     */

    List<String> stackTrace = new ArrayList<>();

    for (int i = 1; i < Math.min(lines.length, 6); i++) {

        if (lines[i].trim().startsWith("at ")) {
            stackTrace.add(lines[i].trim());
        }
    }

    /*
     Determine status
     */

    String statusCode = category == FailureCategory.DOWNSTREAM_DEPENDENCY ? "502" : "500";

    String endpoint = null;

    if (downstreamService != null && !downstreamService.equalsIgnoreCase("unknown")) {
        endpoint = "/api/" + camelToKebab(downstreamService);
    }

    return RootCauseAnalysis.builder()
            .rootException(exceptionClass)
            .rootMessage(exceptionMessage)
            .category(category)
            .downstreamService(downstreamService)
            .detectedAt("Application")
            .relevantStackTrace(stackTrace)
            .statusCode(statusCode)
            .httpMethod("POST")
            .endpoint(endpoint)
            .build();
}