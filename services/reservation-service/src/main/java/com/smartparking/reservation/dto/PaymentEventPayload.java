package com.smartparking.reservation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PaymentEventPayload {
    private UUID reservationId;
    private UUID userId;
    private BigDecimal amount;
    private String currency;
}
