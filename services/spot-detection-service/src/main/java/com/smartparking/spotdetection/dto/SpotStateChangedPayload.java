package com.smartparking.spotdetection.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SpotStateChangedPayload {
    private String spotId;
    private String spotNumber;
    private String lotId;
    private String previousState;
    private String newState;
    private double latitude;
    private double longitude;
    private String source;
}
