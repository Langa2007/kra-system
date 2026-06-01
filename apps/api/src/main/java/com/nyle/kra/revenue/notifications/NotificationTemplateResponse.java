package com.nyle.kra.revenue.notifications;

import java.time.Instant;
import java.util.UUID;

public record NotificationTemplateResponse(
        UUID id,
        String code,
        String channel,
        String subjectTemplate,
        String bodyTemplate,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
