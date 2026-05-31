package com.nyle.kra.revenue.taxgap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record TaxpayerGapRankingResponse(
        UUID taxpayerId,
        String taxpayerPin,
        String taxpayerName,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal score,
        BigDecimal confidenceScore,
        BigDecimal estimatedGap,
        BigDecimal estimatedRecoverableTax,
        BigDecimal estimatedTotalDue,
        JsonNode mainFactors
) {
}
