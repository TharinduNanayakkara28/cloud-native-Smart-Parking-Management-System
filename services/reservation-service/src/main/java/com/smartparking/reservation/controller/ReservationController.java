package com.smartparking.reservation.controller;

import com.smartparking.reservation.dto.CreateReservationRequest;
import com.smartparking.reservation.dto.ReservationResponse;
import com.smartparking.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponse> create(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.createReservation(userId, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getById(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(reservationService.getReservation(id, userId));
    }

    @GetMapping("/user/me")
    public ResponseEntity<List<ReservationResponse>> getMyReservations(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(reservationService.getMyReservations(userId));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ReservationResponse> cancel(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(reservationService.cancel(id, userId));
    }

    @PostMapping("/{id}/checkin")
    public ResponseEntity<ReservationResponse> checkin(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(reservationService.checkin(id, userId));
    }

    @PostMapping("/{id}/checkout")
    public ResponseEntity<ReservationResponse> checkout(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(reservationService.checkout(id, userId));
    }
}
