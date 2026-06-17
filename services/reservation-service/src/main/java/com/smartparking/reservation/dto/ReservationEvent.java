package com.smartparking.reservation.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ReservationEvent {
    private String eventType;
    private Instant timestamp;
    private ReservationEventPayload payload;
}
