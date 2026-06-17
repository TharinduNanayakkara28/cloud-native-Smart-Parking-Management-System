package com.smartparking.reservation.repository;

import com.smartparking.reservation.model.Reservation;
import com.smartparking.reservation.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    List<Reservation> findByUserId(UUID userId);
    List<Reservation> findByStatusAndReservedUntilBefore(ReservationStatus status, Instant time);
}
