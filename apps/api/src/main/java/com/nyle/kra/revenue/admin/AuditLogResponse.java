package com.nyle.kra.revenue.admin;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String action,
        String actorEmail,
        String entityType,
        UUID entityId,
        String ipAddress,
        String userAgent,
        String details,
        OffsetDateTime createdAt
) {
}

