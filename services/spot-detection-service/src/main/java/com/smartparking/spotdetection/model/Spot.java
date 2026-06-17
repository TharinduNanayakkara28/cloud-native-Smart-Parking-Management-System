package com.smartparking.spotdetection.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "spots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Spot {

    @Id
    private UUID id;

    @Column(name = "lot_id", nullable = false)
    private UUID lotId;

    @Column(name = "spot_number", nullable = false, length = 10)
    private String spotNumber;

    @Column(nullable = false)
    private int floor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpotState state;

    @Column(name = "last_updated")
    private Instant lastUpdated;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;
}
