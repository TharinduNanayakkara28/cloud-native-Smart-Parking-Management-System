package com.smartparking.notification.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PenaltyEventPayload {
    private UUID penaltyId;
    private UUID reservationId;
    private UUID userId;
    private UUID spotId;
    private String type;
    private int tier;
    private BigDecimal amount;
    private String status;
}
