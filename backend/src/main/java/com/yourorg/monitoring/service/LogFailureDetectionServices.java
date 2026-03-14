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