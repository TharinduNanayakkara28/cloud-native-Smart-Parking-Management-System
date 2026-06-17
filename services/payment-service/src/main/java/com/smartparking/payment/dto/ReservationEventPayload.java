package com.smartparking.payment.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class ReservationEventPayload {
    private UUID reservationId;
    private UUID userId;
    private UUID spotId;
    private String vehiclePlate;
    private String status;
    private Instant reservedFrom;
    private Instant reservedUntil;
    private Instant checkedInAt;
    private Instant checkedOutAt;
}
