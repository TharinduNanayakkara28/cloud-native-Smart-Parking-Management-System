package com.smartparking.analytics.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.analytics.model.AnalyticsEvent;
import com.smartparking.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PenaltyEventConsumer {

    private final AnalyticsService analyticsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "penalty-events", groupId = "analytics-group")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            JsonNode root    = objectMapper.readTree(record.value());
            JsonNode payload = root.path("payload");

            String eventType = root.path("eventType").asText();
            Instant eventTime = parseInstant(root.path("timestamp").asText(null), Instant.now());

            UUID userId    = parseUuid(payload.path("userId").asText(null));
            UUID penaltyId = parseUuid(payload.path("penaltyId").asText(null));
            BigDecimal amount = parseBigDecimal(payload.path("amount").asText(null));
            Integer tier   = payload.hasNonNull("tier") ? payload.path("tier").asInt() : null;

            analyticsService.record(AnalyticsEvent.builder()
                    .eventType(eventType)
                    .topic("penalty-events")
                    .userId(userId)
                    .entityId(penaltyId)
                    .amount(amount)
                    .tier(tier)
                    .eventTime(eventTime)
                    .rawPayload(record.value())
                    .build());

        } catch (Exception e) {
            log.error("Failed to ingest penalty-event [offset={}]: {}", record.offset(), e.getMessage());
        }
    }

    private UUID parseUuid(String value) {
        try { return value != null ? UUID.fromString(value) : null; } catch (Exception e) { return null; }
    }

    private Instant parseInstant(String value, Instant fallback) {
        try { return value != null && !value.isBlank() ? Instant.parse(value) : fallback; } catch (Exception e) { return fallback; }
    }

    private BigDecimal parseBigDecimal(String value) {
        try { return value != null ? new BigDecimal(value) : null; } catch (Exception e) { return null; }
    }
}
