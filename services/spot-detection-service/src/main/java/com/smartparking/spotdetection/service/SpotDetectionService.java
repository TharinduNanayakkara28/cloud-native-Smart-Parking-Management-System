package com.smartparking.spotdetection.service;

import com.smartparking.spotdetection.dto.*;
import com.smartparking.spotdetection.kafka.SpotStatePublisher;
import com.smartparking.spotdetection.model.Spot;
import com.smartparking.spotdetection.model.SpotEventRecord;
import com.smartparking.spotdetection.model.SpotState;
import com.smartparking.spotdetection.repository.SpotEventRecordRepository;
import com.smartparking.spotdetection.repository.SpotRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpotDetectionService {

    private final SpotRepository spotRepository;
    private final SpotEventRecordRepository eventRecordRepository;
    private final SpotStatePublisher publisher;

    @Transactional
    public void processEvent(SpotSensorEvent event) {
        SpotSensorPayload payload = event.getPayload();
        UUID spotId = UUID.fromString(payload.getSpotId());

        Spot spot = spotRepository.findById(spotId).orElse(null);
        if (spot == null) {
            log.warn("Received event for unknown spotId: {}", spotId);
            return;
        }

        SpotState previousState = spot.getState();
        SpotState newState = SpotState.valueOf(payload.getState());

        spot.setState(newState);
        spot.setLastUpdated(Instant.now());
        spotRepository.save(spot);

        SpotEventRecord record = SpotEventRecord.builder()
                .spotId(spotId)
                .state(newState.name())
                .source(payload.getSource())
                .build();
        eventRecordRepository.save(record);

        SpotStateChangedEvent outbound = SpotStateChangedEvent.builder()
                .eventType("spot.state.changed")
                .timestamp(Instant.now())
                .payload(SpotStateChangedPayload.builder()
                        .spotId(spot.getId().toString())
                        .spotNumber(spot.getSpotNumber())
                        .lotId(spot.getLotId().toString())
                        .previousState(previousState.name())
                        .newState(newState.name())
                        .latitude(spot.getLatitude())
                        .longitude(spot.getLongitude())
                        .source(payload.getSource())
                        .build())
                .build();

        publisher.publish(outbound);
        log.info("Spot {} state: {} → {}", spot.getSpotNumber(), previousState, newState);
    }

    public List<SpotResponse> getAllSpots() {
        return spotRepository.findAll().stream()
                .map(this::toResponse)
                .sorted((a, b) -> a.getSpotNumber().compareTo(b.getSpotNumber()))
                .collect(Collectors.toList());
    }

    public SpotResponse getSpot(UUID spotId) {
        return spotRepository.findById(spotId)
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found: " + spotId));
    }

    private SpotResponse toResponse(Spot s) {
        return SpotResponse.builder()
                .id(s.getId())
                .lotId(s.getLotId())
                .spotNumber(s.getSpotNumber())
                .floor(s.getFloor())
                .state(s.getState().name())
                .latitude(s.getLatitude())
                .longitude(s.getLongitude())
                .lastUpdated(s.getLastUpdated())
                .build();
    }
}
