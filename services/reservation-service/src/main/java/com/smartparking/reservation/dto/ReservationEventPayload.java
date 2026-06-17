package com.smartparking.reservation.dto;

import com.smartparking.reservation.model.ReservationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ReservationEventPayload {
    private UUID reservationId;
    private UUID userId;
    private UUID spotId;
    private String vehiclePlate;
    private ReservationStatus status;
    private Instant reservedFrom;
    private Instant reservedUntil;
    private Instant checkedInAt;
    private Instant checkedOutAt;
}
