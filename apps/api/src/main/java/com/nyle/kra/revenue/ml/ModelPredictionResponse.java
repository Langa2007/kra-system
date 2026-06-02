package com.nyle.kra.revenue.ml;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record ModelPredictionResponse(
        UUID id,
        UUID modelVersionId,
        UUID taxpayerId,
        String kraPin,
        String legalName,
        String predictionType,
        BigDecimal score,
        JsonNode explanation,
        Instant createdAt
) {
}
