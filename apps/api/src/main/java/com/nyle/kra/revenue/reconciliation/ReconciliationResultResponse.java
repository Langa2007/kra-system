package com.nyle.kra.revenue.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record ReconciliationResultResponse(
        UUID id,
        LocalDate reconciliationDate,
        String collectingAgency,
        String revenueChannel,
        BigDecimal expectedAmount,
        BigDecimal settledAmount,
        BigDecimal varianceAmount,
        int transactionCount,
        int settlementCount,
        String settlementStatus,
        String expectedSettlementAccount,
        String settlementAccount,
        Integer maxSettlementLagDays,
        JsonNode evidence,
        UUID riskSignalId,
        Instant createdAt
) {
}
