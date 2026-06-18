package com.smartparking.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class EventRecordResponse {
    private UUID id;
    private String eventType;
    private String topic;
    private UUID userId;
    private UUID entityId;
    private BigDecimal amount;
    private Integer tier;
    private Instant eventTime;
    private Instant receivedAt;
}
