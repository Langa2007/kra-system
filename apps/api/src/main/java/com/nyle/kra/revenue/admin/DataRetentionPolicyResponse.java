package com.nyle.kra.revenue.admin;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DataRetentionPolicyResponse(
        UUID id,
        String dataCategory,
        int retentionDays,
        String policyReason,
        boolean active,
        OffsetDateTime createdAt
) {
}

