package com.nyle.kra.revenue.ml;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TaxpayerRiskFeature(
        UUID taxpayerId,
        String kraPin,
        String legalName,
        String sectorName,
        String county,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal declaredSales,
        BigDecimal declaredIncome,
        BigDecimal invoiceSales,
        BigDecimal customsLandedCost,
        BigDecimal withholdingIncome,
        BigDecimal riskSignalGap,
        BigDecimal ruleScore,
        int returnCount,
        int invoiceCount,
        int customsCount,
        int withholdingCount,
        int openSignalCount
) {
}
