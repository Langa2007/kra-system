package com.nyle.kra.revenue.admin;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PrivacyImpactItemResponse(
        UUID id,
        String dataCategory,
        String purpose,
        String lawfulBasis,
        String dataMinimizationNote,
        boolean maskingRequired,
        boolean completed,
        OffsetDateTime createdAt
) {
}

