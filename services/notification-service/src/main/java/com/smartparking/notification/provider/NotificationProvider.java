package com.smartparking.notification.provider;

import com.smartparking.notification.model.Notification;

/**
 * Abstraction over delivery channels (email, SMS, push).
 * Swap MockNotificationProvider for a real implementation per @Profile.
 */
public interface NotificationProvider {
    void deliver(Notification notification);
}
