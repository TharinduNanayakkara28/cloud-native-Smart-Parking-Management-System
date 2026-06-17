package com.smartparking.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class ParkingSpot {
    private final UUID id;
    private final String spotNumber;
    private final UUID lotId;
    private final double latitude;
    private final double longitude;

    @Setter
    private volatile SpotState state;
}
