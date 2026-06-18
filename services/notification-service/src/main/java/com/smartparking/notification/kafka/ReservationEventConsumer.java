package com.smartparking.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.notification.dto.ReservationEvent;
import com.smartparking.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "reservation-events", groupId = "notification-group")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            ReservationEvent event = objectMapper.readValue(record.value(), ReservationEvent.class);
            notificationService.handleReservationEvent(event);
        } catch (Exception e) {
            log.error("Failed to process reservation-event [offset={}]: {}", record.offset(), e.getMessage());
        }
    }
}
