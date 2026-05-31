package com.nyle.kra.revenue.resolution;

import java.util.UUID;

public record DuplicateTaxpayerCandidate(
        UUID sourceTaxpayerId,
        UUID targetTaxpayerId,
        String sourceName,
        String targetName,
        String matchBasis,
        double confidenceScore
) {
}
