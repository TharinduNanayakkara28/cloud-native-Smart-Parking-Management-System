package com.smartparking.notification.provider;

import com.smartparking.notification.model.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock provider: logs to console instead of sending real email/SMS.
 * Replace with a @Profile("prod") bean backed by JavaMailSender or Twilio.
 */
@Component
@Slf4j
public class MockNotificationProvider implements NotificationProvider {

    @Override
    public void deliver(Notification notification) {
        log.info("[NOTIFICATION] user={} channel={} type={} | {} — {}",
                notification.getUserId(),
                notification.getChannel(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage());
    }
}
