package com.smartparking.penalty.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class ReservationEventPayload {
    private UUID reservationId;
    private UUID userId;
    private UUID spotId;
    private Instant reservedUntil;
}
