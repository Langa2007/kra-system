package com.nyle.kra.revenue.notifications;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID taxpayerId,
        UUID caseId,
        UUID riskSignalId,
        String channel,
        String templateCode,
        String recipient,
        String subject,
        String messageBody,
        String status,
        String deliveryProvider,
        String deliveryReference,
        String responseStatus,
        String responseBody,
        Instant sentAt,
        Instant createdAt,
        Instant updatedAt
) {
}
