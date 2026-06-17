package com.smartparking.spotdetection.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "spot_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpotEventRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "spot_id", nullable = false)
    private UUID spotId;

    @Column(nullable = false, length = 20)
    private String state;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
