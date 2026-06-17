package com.smartparking.reservation.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.reservation.dto.ReservationEvent;
import com.smartparking.reservation.dto.ReservationEventPayload;
import com.smartparking.reservation.model.Reservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationEventPublisher {

    static final String TOPIC = "reservation-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(Reservation reservation, String eventType) {
        ReservationEvent event = ReservationEvent.builder()
                .eventType(eventType)
                .timestamp(Instant.now())
                .payload(ReservationEventPayload.builder()
                        .reservationId(reservation.getId())
                        .userId(reservation.getUserId())
                        .spotId(reservation.getSpotId())
                        .vehiclePlate(reservation.getVehiclePlate())
                        .status(reservation.getStatus())
                        .reservedFrom(reservation.getReservedFrom())
                        .reservedUntil(reservation.getReservedUntil())
                        .checkedInAt(reservation.getCheckedInAt())
                        .checkedOutAt(reservation.getCheckedOutAt())
                        .build())
                .build();
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, reservation.getSpotId().toString(), json);
            log.info("Published {} for reservation {}", eventType, reservation.getId());
        } catch (Exception e) {
            log.error("Failed to publish {} for reservation {}: {}", eventType, reservation.getId(), e.getMessage());
        }
    }
}
