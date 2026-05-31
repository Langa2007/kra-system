package com.nyle.kra.revenue.rules;

import com.fasterxml.jackson.databind.JsonNode;

public record RuleThresholdUpdateRequest(
        JsonNode thresholdConfig,
        String severity,
        Boolean active
) {
}
