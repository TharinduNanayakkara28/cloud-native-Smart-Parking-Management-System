package com.smartparking.availability.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class SpotStateUpdate {
    private String spotId;
    private String spotNumber;
    private String lotId;
    private String previousState;
    private String newState;
    private double latitude;
    private double longitude;
    private Instant timestamp;
}
