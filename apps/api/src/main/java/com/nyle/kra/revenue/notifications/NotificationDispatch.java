package com.nyle.kra.revenue.notifications;

import java.util.UUID;

public record NotificationDispatch(
        UUID notificationId,
        String channel,
        String recipient,
        String subject,
        String messageBody
) {
}
