package com.smartparking.penalty.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "penalties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Penalty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "spot_id", nullable = false)
    private UUID spotId;

    /** WARNING | FINE | ESCALATED */
    @Column(nullable = false, length = 20)
    private String type;

    /** 1 = warning, 2 = fine, 3 = escalated */
    @Column(nullable = false)
    private int tier;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PenaltyStatus status;

    @Column(name = "issued_at", updatable = false)
    private Instant issuedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @PrePersist
    protected void onCreate() {
        this.issuedAt = Instant.now();
    }
}
