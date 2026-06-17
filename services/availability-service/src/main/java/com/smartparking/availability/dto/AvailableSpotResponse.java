package com.smartparking.availability.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AvailableSpotResponse {
    private UUID id;
    private UUID lotId;
    private String spotNumber;
    private int floor;
    private double latitude;
    private double longitude;
    private String state;
}
