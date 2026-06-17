package com.smartparking.spotdetection.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.spotdetection.dto.SpotSensorEvent;
import com.smartparking.spotdetection.service.SpotDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SpotEventConsumer {

    private final SpotDetectionService spotDetectionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "spot-events", groupId = "spot-detection-group")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            SpotSensorEvent event = objectMapper.readValue(record.value(), SpotSensorEvent.class);
            spotDetectionService.processEvent(event);
        } catch (Exception e) {
            log.error("Failed to process message from spot-events [key={}]: {}", record.key(), e.getMessage());
        }
    }
}
