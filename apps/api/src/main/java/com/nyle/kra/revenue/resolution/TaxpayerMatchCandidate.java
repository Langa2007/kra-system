package com.nyle.kra.revenue.resolution;

import java.util.UUID;

public record TaxpayerMatchCandidate(
        UUID taxpayerId,
        String kraPin,
        String registrationNumber,
        String legalName,
        String tradingName,
        String matchBasis,
        double confidenceScore,
        boolean autoLinkEligible
) {
}
