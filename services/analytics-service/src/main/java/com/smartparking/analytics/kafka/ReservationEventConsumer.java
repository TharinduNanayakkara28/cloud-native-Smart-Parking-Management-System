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

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationEventConsumer {

    private final AnalyticsService analyticsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "reservation-events", groupId = "analytics-group")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            JsonNode root    = objectMapper.readTree(record.value());
            JsonNode payload = root.path("payload");

            String eventType = root.path("eventType").asText();
            Instant eventTime = parseInstant(root.path("timestamp").asText(), Instant.now());

            UUID userId        = parseUuid(payload.path("userId").asText(null));
            UUID reservationId = parseUuid(payload.path("reservationId").asText(null));

            analyticsService.record(AnalyticsEvent.builder()
                    .eventType(eventType)
                    .topic("reservation-events")
                    .userId(userId)
                    .entityId(reservationId)
                    .eventTime(eventTime)
                    .rawPayload(record.value())
                    .build());

        } catch (Exception e) {
            log.error("Failed to ingest reservation-event [offset={}]: {}", record.offset(), e.getMessage());
        }
    }

    private UUID parseUuid(String value) {
        try { return value != null ? UUID.fromString(value) : null; } catch (Exception e) { return null; }
    }

    private Instant parseInstant(String value, Instant fallback) {
        try { return value != null && !value.isBlank() ? Instant.parse(value) : fallback; } catch (Exception e) { return fallback; }
    }
}
