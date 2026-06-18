package com.smartparking.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {
    private UUID id;
    private UUID userId;
    private String type;
    private String title;
    private String message;
    private String channel;
    private boolean read;
    private Instant createdAt;
    private Instant readAt;
}
