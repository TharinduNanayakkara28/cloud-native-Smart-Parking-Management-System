package com.smartparking.penalty.repository;

import com.smartparking.penalty.model.Penalty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PenaltyRepository extends JpaRepository<Penalty, UUID> {
    List<Penalty> findByUserId(UUID userId);
    boolean existsByReservationIdAndTierGreaterThanEqual(UUID reservationId, int tier);
}
