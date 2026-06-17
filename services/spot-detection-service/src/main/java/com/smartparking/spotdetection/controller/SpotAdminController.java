package com.smartparking.spotdetection.controller;

import com.smartparking.spotdetection.dto.SpotResponse;
import com.smartparking.spotdetection.service.SpotDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/spots")
@RequiredArgsConstructor
public class SpotAdminController {

    private final SpotDetectionService spotDetectionService;

    @GetMapping
    public ResponseEntity<List<SpotResponse>> getAllSpots() {
        return ResponseEntity.ok(spotDetectionService.getAllSpots());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SpotResponse> getSpot(@PathVariable UUID id) {
        return ResponseEntity.ok(spotDetectionService.getSpot(id));
    }
}
