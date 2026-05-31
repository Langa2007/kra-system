package com.nyle.kra.revenue.rules;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record RiskSignalResponse(
        UUID id,
        UUID taxpayerId,
        String taxpayerPin,
        String taxpayerName,
        String ruleCode,
        String signalType,
        String taxHead,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal observedAmount,
        BigDecimal declaredAmount,
        BigDecimal estimatedGap,
        BigDecimal confidenceScore,
        String severity,
        String explanation,
        JsonNode evidence,
        String status,
        Instant createdAt
) {
}
