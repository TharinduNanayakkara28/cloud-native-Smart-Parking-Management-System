package com.smartparking.payment.service;

import com.smartparking.payment.dto.PaymentResponse;
import com.smartparking.payment.dto.ReservationEventPayload;
import com.smartparking.payment.kafka.PaymentEventPublisher;
import com.smartparking.payment.model.Payment;
import com.smartparking.payment.model.PaymentStatus;
import com.smartparking.payment.provider.PaymentProvider;
import com.smartparking.payment.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final PaymentEventPublisher eventPublisher;

    @Value("${payment.hourly-rate:2.00}")
    private BigDecimal hourlyRate;

    @Value("${payment.currency:USD}")
    private String currency;

    /**
     * Called on reservation.created — pre-authorise the full reserved duration.
     * Publishes payment.success → reservation-service transitions reservation to ACTIVE.
     * Publishes payment.failed  → reservation-service cancels the reservation.
     */
    @Transactional
    public void preAuthorise(ReservationEventPayload payload) {
        String idempotencyKey = payload.getReservationId() + ":reservation.created";
        if (paymentRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("Skipping duplicate reservation.created for {}", payload.getReservationId());
            return;
        }

        BigDecimal amount = calculateAmount(payload.getReservedFrom(), payload.getReservedUntil());

        Payment payment = Payment.builder()
                .reservationId(payload.getReservationId())
                .userId(payload.getUserId())
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();
        paymentRepository.save(payment);

        try {
            String providerRef = paymentProvider.hold(amount, idempotencyKey);
            payment.setProviderRef(providerRef);
            payment.setStatus(PaymentStatus.HELD);
            paymentRepository.save(payment);
            eventPublisher.publish(payment, "payment.success");
            log.info("Pre-auth HELD ${} for reservation {}", amount, payload.getReservationId());
        } catch (Exception e) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            eventPublisher.publish(payment, "payment.failed");
            log.warn("Pre-auth FAILED for reservation {}: {}", payload.getReservationId(), e.getMessage());
        }
    }

    /**
     * Called on reservation.completed — capture the actual parking duration.
     * No new Kafka event: the receipt (GET /payments/{reservationId}) reflects the final amount.
     */
    @Transactional
    public void captureActual(ReservationEventPayload payload) {
        paymentRepository.findByReservationId(payload.getReservationId())
                .filter(p -> p.getStatus() == PaymentStatus.HELD)
                .ifPresent(payment -> {
                    BigDecimal actualAmount = resolveActualAmount(payload);
                    try {
                        paymentProvider.capture(payment.getProviderRef(), actualAmount);
                        payment.setAmount(actualAmount);
                        payment.setStatus(PaymentStatus.CHARGED);
                        paymentRepository.save(payment);
                        log.info("Capture CHARGED ${} for reservation {}", actualAmount, payload.getReservationId());
                    } catch (Exception e) {
                        log.error("Capture failed for reservation {}: {}", payload.getReservationId(), e.getMessage());
                    }
                });
    }

    /**
     * Called on reservation.cancelled — refund the held amount if one exists.
     * Publishes payment.refunded → notification-service informs the driver.
     */
    @Transactional
    public void refund(ReservationEventPayload payload) {
        paymentRepository.findByReservationId(payload.getReservationId())
                .filter(p -> p.getStatus() == PaymentStatus.HELD)
                .ifPresent(payment -> {
                    try {
                        paymentProvider.refund(payment.getProviderRef());
                        payment.setStatus(PaymentStatus.REFUNDED);
                        paymentRepository.save(payment);
                        eventPublisher.publish(payment, "payment.refunded");
                        log.info("Refund issued for reservation {}", payload.getReservationId());
                    } catch (Exception e) {
                        log.error("Refund failed for reservation {}: {}", payload.getReservationId(), e.getMessage());
                    }
                });
    }

    public PaymentResponse getByReservationId(UUID reservationId, UUID requestingUserId) {
        Payment payment = paymentRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("No payment found for reservation: " + reservationId));
        if (!payment.getUserId().equals(requestingUserId)) {
            throw new SecurityException("Access denied to payment for reservation: " + reservationId);
        }
        return toResponse(payment);
    }

    // ── pricing ──────────────────────────────────────────────────────────────

    BigDecimal calculateAmount(Instant from, Instant until) {
        if (from == null || until == null) return hourlyRate;
        long hours = ceilHours(Duration.between(from, until));
        return hourlyRate.multiply(BigDecimal.valueOf(hours)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveActualAmount(ReservationEventPayload payload) {
        Instant start = payload.getCheckedInAt() != null ? payload.getCheckedInAt() : payload.getReservedFrom();
        Instant end   = payload.getCheckedOutAt() != null ? payload.getCheckedOutAt() : payload.getReservedUntil();
        return calculateAmount(start, end);
    }

    private long ceilHours(Duration duration) {
        long minutes = duration.toMinutes();
        return Math.max(1L, (minutes + 59) / 60);
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId())
                .reservationId(p.getReservationId())
                .userId(p.getUserId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus())
                .providerRef(p.getProviderRef())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
