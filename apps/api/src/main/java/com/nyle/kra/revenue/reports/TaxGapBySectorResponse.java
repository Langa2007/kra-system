package com.nyle.kra.revenue.reports;

import java.math.BigDecimal;

public record TaxGapBySectorResponse(
        String sectorCode,
        String sectorName,
        String taxHead,
        int estimateCount,
        int taxpayerCount,
        BigDecimal estimatedGap,
        BigDecimal estimatedRecoverableTax,
        BigDecimal estimatedTotalDue,
        BigDecimal averageConfidence
) {
}

