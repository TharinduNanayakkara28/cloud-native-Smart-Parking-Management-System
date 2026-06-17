package com.smartparking.payment.repository;

import com.smartparking.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByReservationId(UUID reservationId);
    boolean existsByIdempotencyKey(String idempotencyKey);
}
