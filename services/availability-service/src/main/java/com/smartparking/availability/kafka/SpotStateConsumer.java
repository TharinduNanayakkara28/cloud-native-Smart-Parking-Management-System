package com.smartparking.availability.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.availability.dto.SpotStateChangedEvent;
import com.smartparking.availability.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SpotStateConsumer {

    private final AvailabilityService availabilityService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "spot-state", groupId = "availability-group")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            SpotStateChangedEvent event = objectMapper.readValue(record.value(), SpotStateChangedEvent.class);
            availabilityService.processStateChanged(event);
        } catch (Exception e) {
            log.error("Failed to process spot-state message [key={}]: {}", record.key(), e.getMessage());
        }
    }
}
