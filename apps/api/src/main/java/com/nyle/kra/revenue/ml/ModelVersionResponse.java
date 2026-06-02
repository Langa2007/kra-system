package com.nyle.kra.revenue.ml;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record ModelVersionResponse(
        UUID id,
        String modelName,
        String version,
        String modelType,
        String trainingDataSummary,
        JsonNode metrics,
        boolean active,
        Instant createdAt
) {
}
