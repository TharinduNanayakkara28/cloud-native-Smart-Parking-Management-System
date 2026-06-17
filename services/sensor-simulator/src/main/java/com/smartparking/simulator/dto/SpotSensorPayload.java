package com.smartparking.simulator.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SpotSensorPayload {
    private String spotId;
    private String spotNumber;
    private String lotId;
    private String state;
    private String source;
}
