package com.smartparking.reservation.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class CreateReservationRequest {

    @NotNull
    private UUID spotId;

    @NotBlank
    @Pattern(regexp = "^[A-Z0-9\\-]{2,20}$", message = "Plate must be 2-20 uppercase alphanumeric characters")
    private String vehiclePlate;

    @NotNull
    private Instant reservedFrom;

    @NotNull
    @Future
    private Instant reservedUntil;
}
