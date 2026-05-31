package com.nyle.kra.revenue.rules;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

record RuleConfig(
        UUID id,
        String code,
        String severity,
        String taxHead,
        JsonNode thresholds
) {
}
