package com.smartparking.availability.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.availability.dto.ReservationEvent;
import com.smartparking.availability.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationEventConsumer {

    private final AvailabilityService availabilityService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "reservation-events", groupId = "availability-group")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            ReservationEvent event = objectMapper.readValue(record.value(), ReservationEvent.class);
            if (event.getPayload() == null || event.getPayload().getSpotId() == null) return;
            String spotId = event.getPayload().getSpotId().toString();

            switch (event.getEventType()) {
                case "reservation.created" ->
                        availabilityService.markSpotState(spotId, "RESERVED");
                case "reservation.expired", "reservation.cancelled", "reservation.completed" ->
                        availabilityService.markSpotState(spotId, "FREE");
                default -> { /* other reservation events don't affect availability */ }
            }
            log.info("Availability updated via {}: spot {} → {}",
                    event.getEventType(), spotId, event.getEventType().equals("reservation.created") ? "RESERVED" : "FREE");
        } catch (Exception e) {
            log.error("Failed to process reservation-event [key={}]: {}", record.key(), e.getMessage());
        }
    }
}
