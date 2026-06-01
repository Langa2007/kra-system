package com.nyle.kra.revenue.notifications;

import org.springframework.stereotype.Component;

@Component
public class LocalEmailNotificationAdapter implements NotificationDeliveryAdapter {

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public NotificationDeliveryResult send(NotificationDispatch dispatch) {
        return new NotificationDeliveryResult("SENT", "LOCAL_EMAIL", "email-" + dispatch.notificationId());
    }
}
