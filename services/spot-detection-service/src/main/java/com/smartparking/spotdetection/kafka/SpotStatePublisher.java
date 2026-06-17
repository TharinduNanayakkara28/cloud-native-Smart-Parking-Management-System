package com.smartparking.spotdetection.kafka;

import com.smartparking.spotdetection.dto.SpotStateChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SpotStatePublisher {

    static final String TOPIC = "spot-state";

    private final KafkaTemplate<String, SpotStateChangedEvent> kafkaTemplate;

    public void publish(SpotStateChangedEvent event) {
        String spotId = event.getPayload().getSpotId();
        kafkaTemplate.send(TOPIC, spotId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish spot.state.changed for spot {}: {}", spotId, ex.getMessage());
                    } else {
                        log.debug("Published spot.state.changed for spot {}: {} → {}",
                                event.getPayload().getSpotNumber(),
                                event.getPayload().getPreviousState(),
                                event.getPayload().getNewState());
                    }
                });
    }
}
