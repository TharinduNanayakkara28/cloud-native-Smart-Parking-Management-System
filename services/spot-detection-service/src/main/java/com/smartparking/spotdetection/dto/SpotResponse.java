package com.smartparking.spotdetection.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class SpotResponse {
    private UUID id;
    private UUID lotId;
    private String spotNumber;
    private int floor;
    private String state;
    private double latitude;
    private double longitude;
    private Instant lastUpdated;
}
