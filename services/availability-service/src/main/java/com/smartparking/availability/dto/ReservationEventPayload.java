package com.smartparking.availability.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class ReservationEventPayload {
    private UUID reservationId;
    private UUID spotId;
    private String status;
}
