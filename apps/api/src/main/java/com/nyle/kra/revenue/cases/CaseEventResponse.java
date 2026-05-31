package com.nyle.kra.revenue.cases;

import java.time.Instant;
import java.util.UUID;

public record CaseEventResponse(
        UUID id,
        UUID caseId,
        String eventType,
        String eventNote,
        UUID createdBy,
        String createdByName,
        Instant createdAt
) {
}
