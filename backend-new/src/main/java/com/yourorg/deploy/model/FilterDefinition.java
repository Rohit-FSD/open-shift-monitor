package com.yourorg.deploy.model;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class FilterDefinition {
    private String id;
    private String name;
    private String description;
    private FilterCategory category; // SUCCESS | FAILURE | ATTEMPT
    private Expression expression;
    private String color;
    private boolean active;
    private int priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum FilterCategory { SUCCESS, FAILURE, ATTEMPT }

    @Data
    public static class Expression {
        private String operator; // AND | OR | NOT
        private List<Condition> conditions;
        private List<Expression> subExpressions;
    }

    @Data
    public static class Condition {
        private String field;      // message | level | thread | tid | correlationId
        private String condition;  // CONTAINS | EQUALS | REGEX | STARTS_WITH | ENDS_WITH | NOT_CONTAINS
        private String value;
        private String operator;   // chaining operator within conditions (optional)
    }
}
