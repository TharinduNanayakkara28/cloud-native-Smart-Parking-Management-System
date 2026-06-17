package com.smartparking.simulator.controller;

import com.smartparking.simulator.dto.SpotResponse;
import com.smartparking.simulator.service.SimulatorService;
import com.smartparking.simulator.service.SpotRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/simulate")
@RequiredArgsConstructor
public class SimulatorController {

    private final SimulatorService simulatorService;
    private final SpotRegistry registry;

    @PostMapping("/auto/start")
    public ResponseEntity<Map<String, Object>> startAuto() {
        simulatorService.startAuto();
        return ResponseEntity.ok(Map.of("autoRunning", true, "message", "Auto simulation started"));
    }

    @PostMapping("/auto/stop")
    public ResponseEntity<Map<String, Object>> stopAuto() {
        simulatorService.stopAuto();
        return ResponseEntity.ok(Map.of("autoRunning", false, "message", "Auto simulation stopped"));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "autoRunning", simulatorService.isAutoRunning(),
                "spotCount", registry.getAll().size()
        ));
    }

    @GetMapping("/spots")
    public ResponseEntity<List<SpotResponse>> listSpots() {
        List<SpotResponse> spots = registry.getAll().stream()
                .map(s -> SpotResponse.builder()
                        .id(s.getId().toString())
                        .spotNumber(s.getSpotNumber())
                        .lotId(s.getLotId().toString())
                        .latitude(s.getLatitude())
                        .longitude(s.getLongitude())
                        .state(s.getState().name())
                        .build())
                .sorted((a, b) -> a.getSpotNumber().compareTo(b.getSpotNumber()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(spots);
    }

    @PostMapping("/spot/{id}/occupy")
    public ResponseEntity<Void> occupy(@PathVariable UUID id) {
        simulatorService.occupy(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/spot/{id}/free")
    public ResponseEntity<Void> free(@PathVariable UUID id) {
        simulatorService.free(id);
        return ResponseEntity.ok().build();
    }
}
