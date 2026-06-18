package com.smartparking.penalty.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.penalty.dto.ReservationEvent;
import com.smartparking.penalty.dto.ReservationEventPayload;
import com.smartparking.penalty.service.PenaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationEventConsumer {

    private final PenaltyService penaltyService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "reservation-events", groupId = "penalty-group")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            ReservationEvent event = objectMapper.readValue(record.value(), ReservationEvent.class);
            ReservationEventPayload payload = event.getPayload();
            if (payload == null || payload.getReservationId() == null) return;

            switch (event.getEventType()) {
                case "reservation.active" ->
                        penaltyService.startOverstayTimer(payload);
                case "reservation.completed", "reservation.cancelled", "reservation.expired" ->
                        penaltyService.cancelOverstayTimer(payload.getReservationId());
                default -> log.debug("Ignoring reservation event: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Failed to process reservation-event [key={}]: {}", record.key(), e.getMessage());
        }
    }
}
