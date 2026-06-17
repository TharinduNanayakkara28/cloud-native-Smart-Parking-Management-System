package com.smartparking.payment.controller;

import com.smartparking.payment.dto.PaymentResponse;
import com.smartparking.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/{reservationId}")
    public ResponseEntity<PaymentResponse> getPayment(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID reservationId) {
        return ResponseEntity.ok(paymentService.getByReservationId(reservationId, userId));
    }
}
