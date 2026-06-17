package com.smartparking.availability.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Static location index — no state column.
 * State is stored exclusively in Redis (spot:{id}:state).
 * The location GEOGRAPHY column exists in the DB for PostGIS queries
 * but is not mapped here; the native query selects specific columns only.
 */
@Entity
@Table(name = "availability_spots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilitySpot {

    @Id
    private UUID id;

    @Column(name = "lot_id", nullable = false)
    private UUID lotId;

    @Column(name = "spot_number", nullable = false, length = 10)
    private String spotNumber;

    @Column(nullable = false)
    private int floor;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;
}
