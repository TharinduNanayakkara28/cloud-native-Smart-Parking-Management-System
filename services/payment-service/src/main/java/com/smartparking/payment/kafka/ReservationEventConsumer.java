package com.smartparking.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.payment.dto.ReservationEvent;
import com.smartparking.payment.dto.ReservationEventPayload;
import com.smartparking.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationEventConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "reservation-events", groupId = "payment-group")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            ReservationEvent event = objectMapper.readValue(record.value(), ReservationEvent.class);
            ReservationEventPayload payload = event.getPayload();
            if (payload == null || payload.getReservationId() == null) return;

            switch (event.getEventType()) {
                case "reservation.created"   -> paymentService.preAuthorise(payload);
                case "reservation.completed" -> paymentService.captureActual(payload);
                case "reservation.cancelled" -> paymentService.refund(payload);
                default -> log.debug("Ignoring reservation event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Failed to process reservation-event [key={}]: {}", record.key(), e.getMessage());
        }
    }
}
