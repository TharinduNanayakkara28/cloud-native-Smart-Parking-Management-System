package com.smartparking.penalty.controller;

import com.smartparking.penalty.dto.PenaltyResponse;
import com.smartparking.penalty.service.PenaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/penalties")
@RequiredArgsConstructor
public class PenaltyController {

    private final PenaltyService penaltyService;

    @GetMapping("/user/me")
    public ResponseEntity<List<PenaltyResponse>> getMyPenalties(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(penaltyService.getMyPenalties(userId));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<PenaltyResponse> pay(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(penaltyService.pay(id, userId));
    }
}
