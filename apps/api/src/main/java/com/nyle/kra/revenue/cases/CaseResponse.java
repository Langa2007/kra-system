package com.nyle.kra.revenue.cases;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CaseResponse(
        UUID id,
        String caseNumber,
        UUID riskSignalId,
        UUID taxpayerId,
        String taxpayerPin,
        String taxpayerName,
        String title,
        String caseType,
        String priority,
        String status,
        BigDecimal estimatedRecoverableAmount,
        UUID assignedTo,
        String assignedOfficerName,
        Instant openedAt,
        Instant closedAt,
        String closureReason,
        BigDecimal assessedAmount,
        BigDecimal agreedAmount,
        BigDecimal collectedAmount
) {
}
