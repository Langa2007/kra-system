package com.nyle.kra.revenue.rules;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record RuleDefinitionResponse(
        UUID id,
        String code,
        String name,
        String description,
        String taxHead,
        String ruleType,
        String severity,
        JsonNode thresholdConfig,
        boolean active,
        Instant updatedAt
) {
}
