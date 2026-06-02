package com.nyle.kra.revenue.graph;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record GraphEdgeResponse(
        UUID id,
        String sourceType,
        UUID sourceId,
        String sourceLabel,
        String targetType,
        UUID targetId,
        String targetLabel,
        String edgeType,
        BigDecimal weight,
        String source,
        Map<String, Object> evidence,
        Instant createdAt
) {
}
