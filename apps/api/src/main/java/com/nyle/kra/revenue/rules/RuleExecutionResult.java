package com.nyle.kra.revenue.rules;

public record RuleExecutionResult(
        String ruleCode,
        int signalsTouched
) {
}
