package com.smartparking.reservation.kafka;

import com.smartparking.reservation.dto.PaymentEvent;
import com.smartparking.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final ReservationService reservationService;

    @KafkaListener(
            topics = "payment-events",
            groupId = "reservation-group",
            containerFactory = "paymentEventListenerFactory"
    )
    public void consume(PaymentEvent event) {
        if (event.getPayload() == null || event.getPayload().getReservationId() == null) {
            log.warn("Received payment event with null payload or reservationId, skipping");
            return;
        }
        try {
            switch (event.getEventType()) {
                case "payment.success" -> reservationService.activateFromPayment(event.getPayload().getReservationId());
                case "payment.failed"  -> reservationService.cancelFromPayment(event.getPayload().getReservationId());
                default -> log.debug("Ignoring payment event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Failed to process payment event {} for reservation {}: {}",
                    event.getEventType(), event.getPayload().getReservationId(), e.getMessage());
        }
    }
}
