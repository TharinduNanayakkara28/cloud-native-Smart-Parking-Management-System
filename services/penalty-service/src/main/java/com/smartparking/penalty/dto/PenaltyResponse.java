package com.smartparking.penalty.dto;

import com.smartparking.penalty.model.PenaltyStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PenaltyResponse {
    private UUID id;
    private UUID reservationId;
    private UUID userId;
    private UUID spotId;
    private String type;
    private int tier;
    private BigDecimal amount;
    private PenaltyStatus status;
    private Instant issuedAt;
    private Instant paidAt;
}
