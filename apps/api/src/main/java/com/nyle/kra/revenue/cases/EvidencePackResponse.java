package com.nyle.kra.revenue.cases;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record EvidencePackResponse(
        UUID id,
        UUID caseId,
        int version,
        String summary,
        JsonNode evidence,
        String fileUri,
        UUID generatedBy,
        String generatedByName,
        Instant generatedAt
) {
}
