package com.nyle.kra.revenue.resolution;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TaxpayerProfileResponse(
        UUID taxpayerId,
        String kraPin,
        String taxpayerType,
        String legalName,
        String tradingName,
        String registrationNumber,
        String sectorName,
        String county,
        List<Identifier> identifiers,
        Counts recordCounts,
        MoneyTotals totals,
        List<Relationship> relationships
) {

    public record Identifier(String identifierType, String identifierValue, String source, BigDecimal confidenceScore) {
    }

    public record Counts(
            int invoicesAsSupplier,
            int invoicesAsBuyer,
            int taxReturns,
            int customsDeclarations,
            int withholdingAsPayer,
            int withholdingAsPayee,
            int businessPermits,
            int properties,
            int paymentTransactions
    ) {
    }

    public record MoneyTotals(
            BigDecimal supplierInvoiceTotal,
            BigDecimal buyerInvoiceTotal,
            BigDecimal declaredSales,
            BigDecimal customsValue,
            BigDecimal payments
    ) {
    }

    public record Relationship(
            UUID relatedTaxpayerId,
            String relatedPersonName,
            String relationshipType,
            String source,
            BigDecimal confidenceScore
    ) {
    }
}
