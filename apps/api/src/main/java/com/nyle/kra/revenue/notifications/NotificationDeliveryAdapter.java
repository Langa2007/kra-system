package com.nyle.kra.revenue.notifications;

public interface NotificationDeliveryAdapter {

    String channel();

    NotificationDeliveryResult send(NotificationDispatch dispatch);
}
