package com.nyle.kra.revenue.resolution;

public record EntityResolutionSummary(
        int kraPinIdentifiersCreated,
        int registrationIdentifiersCreated,
        int invoiceSupplierLinks,
        int invoiceBuyerLinks,
        int customsLinks,
        int withholdingPayerLinks,
        int withholdingPayeeLinks,
        int propertyLinks,
        int paymentLinks,
        int permitFuzzyLinks,
        int duplicateRelationshipsCreated
) {
    int totalLinksCreated() {
        return invoiceSupplierLinks
                + invoiceBuyerLinks
                + customsLinks
                + withholdingPayerLinks
                + withholdingPayeeLinks
                + propertyLinks
                + paymentLinks
                + permitFuzzyLinks;
    }
}
