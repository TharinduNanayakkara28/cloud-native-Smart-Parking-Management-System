package com.smartparking.simulator.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class SpotSensorEvent {
    private String eventType;
    private Instant timestamp;
    private SpotSensorPayload payload;
}
