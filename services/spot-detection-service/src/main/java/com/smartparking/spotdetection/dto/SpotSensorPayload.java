package com.smartparking.spotdetection.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SpotSensorPayload {
    private String spotId;
    private String spotNumber;
    private String lotId;
    private String state;
    private String source;
}
