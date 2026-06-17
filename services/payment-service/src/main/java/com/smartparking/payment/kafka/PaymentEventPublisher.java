package com.smartparking.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.payment.model.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    static final String TOPIC = "payment-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(Payment payment, String eventType) {
        try {
            Map<String, Object> event = Map.of(
                    "eventType", eventType,
                    "timestamp", Instant.now().toString(),
                    "payload", Map.of(
                            "reservationId", payment.getReservationId().toString(),
                            "userId", payment.getUserId().toString(),
                            "amount", payment.getAmount(),
                            "currency", payment.getCurrency()
                    )
            );
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, payment.getReservationId().toString(), json);
            log.info("Published {} for reservation {}", eventType, payment.getReservationId());
        } catch (Exception e) {
            log.error("Failed to publish {} for reservation {}: {}", eventType, payment.getReservationId(), e.getMessage());
        }
    }
}
