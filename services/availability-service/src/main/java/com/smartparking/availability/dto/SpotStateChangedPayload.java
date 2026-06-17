package com.smartparking.availability.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
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
