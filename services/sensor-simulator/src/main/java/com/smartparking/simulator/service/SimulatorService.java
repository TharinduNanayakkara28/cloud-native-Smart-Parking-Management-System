package com.smartparking.simulator.service;

import com.smartparking.simulator.kafka.SpotEventPublisher;
import com.smartparking.simulator.model.ParkingSpot;
import com.smartparking.simulator.model.SpotState;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimulatorService {

    private final SpotRegistry registry;
    private final SpotEventPublisher publisher;
    private final Random random = new Random();

    private volatile boolean autoRunning = false;

    @Scheduled(fixedDelay = 2000)
    public void autoSimulate() {
        if (!autoRunning) return;

        List<UUID> ids = registry.getAllIds();
        UUID targetId = ids.get(random.nextInt(ids.size()));
        registry.findById(targetId).ifPresent(spot -> {
            SpotState next = spot.getState() == SpotState.FREE ? SpotState.OCCUPIED : SpotState.FREE;
            flipSpot(spot, next);
        });
    }

    public void startAuto() {
        autoRunning = true;
        log.info("Auto simulation started");
    }

    public void stopAuto() {
        autoRunning = false;
        log.info("Auto simulation stopped");
    }

    public boolean isAutoRunning() {
        return autoRunning;
    }

    public void occupy(UUID spotId) {
        ParkingSpot spot = registry.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found: " + spotId));
        flipSpot(spot, SpotState.OCCUPIED);
    }

    public void free(UUID spotId) {
        ParkingSpot spot = registry.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found: " + spotId));
        flipSpot(spot, SpotState.FREE);
    }

    private void flipSpot(ParkingSpot spot, SpotState newState) {
        spot.setState(newState);
        publisher.publish(spot, newState);
        log.info("Spot {} → {}", spot.getSpotNumber(), newState);
    }
}
