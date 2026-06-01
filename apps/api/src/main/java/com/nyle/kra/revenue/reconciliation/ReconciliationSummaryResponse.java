package com.nyle.kra.revenue.reconciliation;

import java.math.BigDecimal;

public record ReconciliationSummaryResponse(
        BigDecimal expectedAmount,
        BigDecimal settledAmount,
        BigDecimal varianceAmount,
        int resultCount,
        int exceptionCount,
        int missingCount,
        int delayedCount,
        int duplicateCount,
        int wrongAccountCount
) {
}
