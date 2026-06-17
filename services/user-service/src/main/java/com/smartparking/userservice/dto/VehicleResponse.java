package com.smartparking.userservice.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class VehicleResponse {
    private UUID id;
    private String plate;
    private String make;
    private String model;
}
