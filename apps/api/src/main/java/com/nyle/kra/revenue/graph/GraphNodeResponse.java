package com.nyle.kra.revenue.graph;

import java.math.BigDecimal;
import java.util.UUID;

public record GraphNodeResponse(
        UUID id,
        String nodeType,
        String label,
        BigDecimal riskScore
) {
}
