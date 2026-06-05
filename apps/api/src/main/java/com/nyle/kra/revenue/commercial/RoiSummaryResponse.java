package com.nyle.kra.revenue.commercial;

import java.math.BigDecimal;

public record RoiSummaryResponse(
        BigDecimal estimatedGap,
        BigDecimal recoverableTax,
        BigDecimal collectedAmount,
        BigDecimal settlementVariance,
        long openCases,
        BigDecimal pilotCost,
        BigDecimal expectedCollectionRate,
        BigDecimal expectedRecoveredRevenue,
        BigDecimal netBenefit,
        BigDecimal roiMultiple,
        BigDecimal paybackMonths
) {
}
