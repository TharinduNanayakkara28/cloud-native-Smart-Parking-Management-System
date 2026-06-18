package com.smartparking.penalty.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.penalty.model.Penalty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PenaltyEventPublisher {

    static final String TOPIC = "penalty-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(Penalty penalty) {
        try {
            Map<String, Object> event = Map.of(
                    "eventType", "penalty.issued",
                    "timestamp", Instant.now().toString(),
                    "payload", Map.of(
                            "penaltyId",     penalty.getId().toString(),
                            "reservationId", penalty.getReservationId().toString(),
                            "userId",        penalty.getUserId().toString(),
                            "spotId",        penalty.getSpotId().toString(),
                            "type",          penalty.getType(),
                            "tier",          penalty.getTier(),
                            "amount",        penalty.getAmount(),
                            "status",        penalty.getStatus().name()
                    )
            );
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, penalty.getReservationId().toString(), json);
            log.info("Published penalty.issued [tier={}] for reservation {}", penalty.getTier(), penalty.getReservationId());
        } catch (Exception e) {
            log.error("Failed to publish penalty.issued for penalty {}: {}", penalty.getId(), e.getMessage());
        }
    }
}
