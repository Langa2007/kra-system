package com.nyle.kra.revenue.reports;

import java.math.BigDecimal;

public record TaxGapByRegionResponse(
        String region,
        String taxHead,
        int estimateCount,
        int taxpayerCount,
        BigDecimal estimatedGap,
        BigDecimal estimatedRecoverableTax,
        BigDecimal estimatedTotalDue,
        BigDecimal averageConfidence
) {
}

