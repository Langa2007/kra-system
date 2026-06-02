package com.nyle.kra.revenue.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueRecoveryResponse(
        LocalDate period,
        String taxHead,
        long recoveryRecords,
        BigDecimal assessedAmount,
        BigDecimal agreedAmount,
        BigDecimal collectedAmount
) {
}

