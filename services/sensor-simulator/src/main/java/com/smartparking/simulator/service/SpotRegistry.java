package com.smartparking.simulator.service;

import com.smartparking.simulator.model.ParkingSpot;
import com.smartparking.simulator.model.SpotState;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SpotRegistry {

    static final UUID LOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");

    private final Map<UUID, ParkingSpot> spots = new ConcurrentHashMap<>();

    @PostConstruct
    public void seed() {
        // 3 rows × 6 spots, lat spaced ~12m apart, lng spaced ~3m apart
        String[] rows = {"A", "B", "C"};
        double[] rowLats = {6.92710, 6.92721, 6.92732};
        double baseLng  = 79.86100;
        double lngStep  = 0.000030;

        int spotIndex = 1;
        for (int row = 0; row < rows.length; row++) {
            for (int col = 1; col <= 6; col++) {
                UUID id = UUID.fromString(String.format("00000000-0000-0000-0000-%012d", spotIndex++));
                String number = rows[row] + col;
                double lat = rowLats[row];
                double lng = baseLng + (col - 1) * lngStep;
                spots.put(id, new ParkingSpot(id, number, LOT_ID, lat, lng, SpotState.FREE));
            }
        }
    }

    public Collection<ParkingSpot> getAll() {
        return Collections.unmodifiableCollection(spots.values());
    }

    public List<UUID> getAllIds() {
        return new ArrayList<>(spots.keySet());
    }

    public Optional<ParkingSpot> findById(UUID id) {
        return Optional.ofNullable(spots.get(id));
    }
}
