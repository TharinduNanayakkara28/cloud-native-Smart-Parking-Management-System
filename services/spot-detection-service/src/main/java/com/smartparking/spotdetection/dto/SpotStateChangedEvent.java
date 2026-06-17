package com.smartparking.spotdetection.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class SpotStateChangedEvent {
    private String eventType;
    private Instant timestamp;
    private SpotStateChangedPayload payload;
}
