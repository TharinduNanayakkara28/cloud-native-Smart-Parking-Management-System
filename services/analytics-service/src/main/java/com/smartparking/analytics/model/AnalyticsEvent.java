package com.smartparking.analytics.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analytics_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** E.g. reservation.created, payment.success, penalty.issued, spot.state.changed */
    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    /** Source Kafka topic */
    @Column(nullable = false, length = 100)
    private String topic;

    /** Nullable: spot-state events have no user */
    @Column(name = "user_id")
    private UUID userId;

    /** spotId / reservationId / paymentId / penaltyId */
    @Column(name = "entity_id")
    private UUID entityId;

    /** Payment and penalty amounts; null for other events */
    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    /** Penalty tier (1-3); null for non-penalty events */
    private Integer tier;

    /** Timestamp from the original event payload */
    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    /** When this service received the Kafka message */
    @Column(name = "received_at", updatable = false)
    private Instant receivedAt;

    /** Full JSON payload for ad-hoc querying */
    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @PrePersist
    protected void onCreate() {
        this.receivedAt = Instant.now();
    }
}
