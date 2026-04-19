package com.yourorg.deploy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorCodeMapping {
    public static final String DEFAULT_CODE = "DEFAULT";

    private String code;
    private String pattern;
    private String severity;
    private String recommendedSolution;
    private String description;
    private List<String> steps;

    private transient Pattern compiledPattern;

    public Pattern compiled() {
        if (compiledPattern == null && pattern != null && !pattern.isEmpty()) {
            compiledPattern = Pattern.compile(pattern);
        }
        return compiledPattern;
    }
}
