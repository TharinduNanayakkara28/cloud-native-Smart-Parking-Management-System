package com.smartparking.simulator.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SpotResponse {
    private String id;
    private String spotNumber;
    private String lotId;
    private double latitude;
    private double longitude;
    private String state;
}
