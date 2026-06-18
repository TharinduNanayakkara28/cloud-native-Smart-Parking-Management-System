package com.smartparking.notification.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class PenaltyEvent {
    private String eventType;
    private Instant timestamp;
    private PenaltyEventPayload payload;
}
