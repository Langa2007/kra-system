package com.nyle.kra.revenue.reports;

import java.math.BigDecimal;

public record AuditPipelineResponse(
        String status,
        long caseCount,
        BigDecimal estimatedRecoverableAmount,
        BigDecimal assessedAmount,
        BigDecimal collectedAmount
) {
}

