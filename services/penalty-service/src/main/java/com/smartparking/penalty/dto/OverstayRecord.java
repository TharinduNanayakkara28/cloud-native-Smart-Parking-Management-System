package com.smartparking.penalty.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverstayRecord {
    private UUID reservationId;
    private UUID userId;
    private UUID spotId;
    private Instant reservedUntil;
    /** Highest tier already issued: 0 = none, 1 = warning, 2 = fine, 3 = escalated */
    private int lastTierIssued;
}
