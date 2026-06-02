package com.nyle.kra.revenue.graph;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record HighRiskClusterResponse(
        String clusterKey,
        UUID sourceTaxpayerId,
        String sourceTaxpayerName,
        UUID targetTaxpayerId,
        String targetTaxpayerName,
        String edgeType,
        BigDecimal edgeWeight,
        BigDecimal sourceRiskScore,
        BigDecimal targetRiskScore,
        List<String> reasons
) {
}
