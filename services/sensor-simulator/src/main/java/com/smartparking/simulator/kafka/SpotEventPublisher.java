package com.smartparking.simulator.kafka;

import com.smartparking.simulator.dto.SpotSensorEvent;
import com.smartparking.simulator.dto.SpotSensorPayload;
import com.smartparking.simulator.model.ParkingSpot;
import com.smartparking.simulator.model.SpotState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class SpotEventPublisher {

    static final String TOPIC = "spot-events";

    private final KafkaTemplate<String, SpotSensorEvent> kafkaTemplate;

    public void publish(ParkingSpot spot, SpotState newState) {
        String eventType = newState == SpotState.OCCUPIED ? "spot.occupied" : "spot.freed";

        SpotSensorEvent event = SpotSensorEvent.builder()
                .eventType(eventType)
                .timestamp(Instant.now())
                .payload(SpotSensorPayload.builder()
                        .spotId(spot.getId().toString())
                        .spotNumber(spot.getSpotNumber())
                        .lotId(spot.getLotId().toString())
                        .state(newState.name())
                        .source("simulator")
                        .build())
                .build();

        kafkaTemplate.send(TOPIC, spot.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event for spot {}: {}", spot.getSpotNumber(), ex.getMessage());
                    } else {
                        log.debug("Published {} for spot {}", eventType, spot.getSpotNumber());
                    }
                });
    }
}
