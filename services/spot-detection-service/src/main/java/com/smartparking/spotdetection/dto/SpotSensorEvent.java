package com.smartparking.spotdetection.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
public class SpotSensorEvent {
    private String eventType;
    private Instant timestamp;
    private SpotSensorPayload payload;
}
