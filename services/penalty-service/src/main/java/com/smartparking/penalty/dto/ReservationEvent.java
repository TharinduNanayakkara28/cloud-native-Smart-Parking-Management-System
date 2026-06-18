package com.smartparking.penalty.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class ReservationEvent {
    private String eventType;
    private Instant timestamp;
    private ReservationEventPayload payload;
}
