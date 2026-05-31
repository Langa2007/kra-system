package com.nyle.kra.revenue.taxgap;

import java.math.BigDecimal;

public record TaxGapSummaryResponse(
        String taxHead,
        int taxpayerCount,
        int estimateCount,
        BigDecimal estimatedGap,
        BigDecimal estimatedRecoverableTax,
        BigDecimal estimatedTotalDue,
        BigDecimal averageConfidence
) {
}
