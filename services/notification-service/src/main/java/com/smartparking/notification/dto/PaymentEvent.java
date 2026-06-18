package com.smartparking.notification.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class PaymentEvent {
    private String eventType;
    private Instant timestamp;
    private PaymentEventPayload payload;
}
