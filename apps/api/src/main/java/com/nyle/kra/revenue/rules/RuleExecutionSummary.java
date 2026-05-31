package com.nyle.kra.revenue.rules;

import java.util.List;

public record RuleExecutionSummary(
        int rulesExecuted,
        int signalsTouched,
        List<RuleExecutionResult> results
) {
}
