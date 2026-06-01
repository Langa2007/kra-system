package com.nyle.kra.revenue.notifications;

import org.springframework.stereotype.Component;

@Component
public class LocalSmsNotificationAdapter implements NotificationDeliveryAdapter {

    @Override
    public String channel() {
        return "SMS";
    }

    @Override
    public NotificationDeliveryResult send(NotificationDispatch dispatch) {
        return new NotificationDeliveryResult("SENT", "LOCAL_SMS", "sms-" + dispatch.notificationId());
    }
}
