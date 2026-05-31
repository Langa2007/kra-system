package com.nyle.kra.revenue.taxgap;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record TaxGapEstimateResponse(
        UUID id,
        UUID taxpayerId,
        String taxpayerPin,
        String taxpayerName,
        String taxHead,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal declaredAmount,
        BigDecimal observedAmount,
        BigDecimal estimatedGap,
        BigDecimal estimatedRecoverableTax,
        BigDecimal estimatedPenalty,
        BigDecimal estimatedInterest,
        BigDecimal estimatedTotalDue,
        BigDecimal confidenceScore,
        JsonNode evidence,
        Instant createdAt
) {
}
