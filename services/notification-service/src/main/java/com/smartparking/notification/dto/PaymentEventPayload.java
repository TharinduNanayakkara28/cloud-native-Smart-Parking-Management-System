package com.smartparking.notification.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PaymentEventPayload {
    private String paymentId;
    private String reservationId;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String providerRef;
}
