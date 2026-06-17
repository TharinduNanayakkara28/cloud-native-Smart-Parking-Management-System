package com.smartparking.availability.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
public class SpotStateChangedEvent {
    private String eventType;
    private Instant timestamp;
    private SpotStateChangedPayload payload;
}
