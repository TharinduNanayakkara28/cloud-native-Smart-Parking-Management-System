package com.smartparking.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VehicleRequest {

    @NotBlank
    @Pattern(regexp = "^[A-Z0-9\\-]{2,20}$", message = "Plate must be 2-20 uppercase alphanumeric characters")
    private String plate;

    private String make;
    private String model;
}
