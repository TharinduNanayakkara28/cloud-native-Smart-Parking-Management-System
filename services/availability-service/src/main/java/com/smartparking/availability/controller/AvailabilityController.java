package com.smartparking.availability.controller;

import com.smartparking.availability.dto.AvailableSpotResponse;
import com.smartparking.availability.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/spots")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping("/available")
    public ResponseEntity<List<AvailableSpotResponse>> getAvailableSpots(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "500") double radius) {
        return ResponseEntity.ok(availabilityService.getAvailableSpots(lat, lng, radius));
    }

    @GetMapping("/state")
    public ResponseEntity<List<AvailableSpotResponse>> getAllSpotStates() {
        return ResponseEntity.ok(availabilityService.getAllSpotStates());
    }

    /**
     * Internal endpoint used by reservation-service to check a spot's state before locking.
     * Not routed through the public gateway — called service-to-service only.
     */
    @GetMapping("/{spotId}/state")
    public ResponseEntity<Map<String, String>> getSpotState(@PathVariable UUID spotId) {
        String state = availabilityService.getSpotState(spotId.toString());
        return ResponseEntity.ok(Map.of("spotId", spotId.toString(), "state", state));
    }
}
