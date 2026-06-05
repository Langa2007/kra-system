package com.nyle.kra.revenue.integration;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record SourceSchemaMappingResponse(
        UUID id,
        UUID dataSourceId,
        String dataSourceCode,
        String targetEntity,
        Map<String, Object> sourceSchema,
        Map<String, Object> mappingConfig,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
