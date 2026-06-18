package com.smartparking.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class RevenueResponse {
    private String period;
    private Instant periodStart;
    private BigDecimal grossRevenue;
    private BigDecimal refundedAmount;
    private BigDecimal netRevenue;
    private long transactionCount;
}
