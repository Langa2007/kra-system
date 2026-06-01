package com.nyle.kra.revenue.notifications;

public record NotificationDeliveryResult(
        String status,
        String provider,
        String providerReference
) {
}
