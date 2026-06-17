package com.smartparking.availability.controller;

import com.smartparking.availability.dto.AvailableSpotResponse;
import com.smartparking.availability.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/spots")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    /**
     * Find available (FREE) spots within radiusMeters of a given location.
     *
     * @param lat          Latitude of the search centre
     * @param lng          Longitude of the search centre
     * @param radius       Search radius in metres (default 500m)
     */
    @GetMapping("/available")
    public ResponseEntity<List<AvailableSpotResponse>> getAvailableSpots(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "500") double radius) {
        return ResponseEntity.ok(availabilityService.getAvailableSpots(lat, lng, radius));
    }

    /**
     * Returns all spots with their current state from the Redis + 5s burst cache.
     * Useful for dashboard views and the Simulation Dashboard UI.
     */
    @GetMapping("/state")
    public ResponseEntity<List<AvailableSpotResponse>> getAllSpotStates() {
        return ResponseEntity.ok(availabilityService.getAllSpotStates());
    }
}
