package com.smartparking.reservation.service;

import com.smartparking.reservation.dto.CreateReservationRequest;
import com.smartparking.reservation.dto.ReservationResponse;
import com.smartparking.reservation.kafka.ReservationEventPublisher;
import com.smartparking.reservation.model.Reservation;
import com.smartparking.reservation.model.ReservationStatus;
import com.smartparking.reservation.repository.ReservationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private static final String LOCK_KEY_PREFIX = "lock:spot:";

    private final ReservationRepository reservationRepository;
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> releaseLockScript;
    private final ReservationEventPublisher eventPublisher;
    private final AvailabilityClient availabilityClient;

    @Transactional
    public ReservationResponse createReservation(UUID userId, CreateReservationRequest request) {
        UUID spotId = request.getSpotId();

        // 1. Optimistic availability check (before competing for the lock)
        String state = availabilityClient.getSpotState(spotId);
        if (!"FREE".equals(state)) {
            throw new IllegalStateException("Spot is not available (current state: " + state + ")");
        }

        // 2. Acquire distributed lock
        String lockKey = LOCK_KEY_PREFIX + spotId;
        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(10));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new IllegalStateException("Spot is currently being reserved, please try again");
        }

        try {
            // 3. Create and persist reservation
            Reservation reservation = Reservation.builder()
                    .userId(userId)
                    .spotId(spotId)
                    .vehiclePlate(request.getVehiclePlate())
                    .status(ReservationStatus.PENDING)
                    .reservedFrom(request.getReservedFrom())
                    .reservedUntil(request.getReservedUntil())
                    .build();
            reservationRepository.save(reservation);

            // 4. Publish event
            eventPublisher.publish(reservation, "reservation.created");

            return toResponse(reservation);
        } finally {
            // 5. Release lock atomically (no-op if TTL already expired and another caller owns it)
            redisTemplate.execute(releaseLockScript, List.of(lockKey), lockValue);
        }
    }

    public ReservationResponse getReservation(UUID reservationId, UUID userId) {
        Reservation reservation = findOwned(reservationId, userId);
        return toResponse(reservation);
    }

    public List<ReservationResponse> getMyReservations(UUID userId) {
        return reservationRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ReservationResponse cancel(UUID reservationId, UUID userId) {
        Reservation reservation = findOwned(reservationId, userId);
        if (reservation.getStatus() == ReservationStatus.COMPLETED
                || reservation.getStatus() == ReservationStatus.EXPIRED) {
            throw new IllegalStateException("Cannot cancel a " + reservation.getStatus() + " reservation");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
        eventPublisher.publish(reservation, "reservation.cancelled");
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse checkin(UUID reservationId, UUID userId) {
        Reservation reservation = findOwned(reservationId, userId);
        if (reservation.getStatus() != ReservationStatus.PENDING
                && reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Cannot check in a " + reservation.getStatus() + " reservation");
        }
        reservation.setCheckedInAt(Instant.now());
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservationRepository.save(reservation);
        // Publish reservation.active so Penalty Service starts the overstay timer
        eventPublisher.publish(reservation, "reservation.active");
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse checkout(UUID reservationId, UUID userId) {
        Reservation reservation = findOwned(reservationId, userId);
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Cannot check out a " + reservation.getStatus() + " reservation");
        }
        reservation.setCheckedOutAt(Instant.now());
        reservation.setStatus(ReservationStatus.COMPLETED);
        reservationRepository.save(reservation);
        eventPublisher.publish(reservation, "reservation.completed");
        return toResponse(reservation);
    }

    // Called by PaymentEventConsumer on payment.success
    @Transactional
    public void activateFromPayment(UUID reservationId) {
        reservationRepository.findById(reservationId).ifPresent(reservation -> {
            if (reservation.getStatus() == ReservationStatus.PENDING) {
                reservation.setStatus(ReservationStatus.ACTIVE);
                reservationRepository.save(reservation);
                eventPublisher.publish(reservation, "reservation.active");
                log.info("Reservation {} activated via payment.success", reservationId);
            }
        });
    }

    // Called by PaymentEventConsumer on payment.failed
    @Transactional
    public void cancelFromPayment(UUID reservationId) {
        reservationRepository.findById(reservationId).ifPresent(reservation -> {
            if (reservation.getStatus() == ReservationStatus.PENDING) {
                reservation.setStatus(ReservationStatus.CANCELLED);
                reservationRepository.save(reservation);
                eventPublisher.publish(reservation, "reservation.cancelled");
                log.info("Reservation {} cancelled via payment.failed", reservationId);
            }
        });
    }

    // Called by ReservationExpiryScheduler
    @Transactional
    public void expirePendingReservations() {
        List<Reservation> overdue = reservationRepository
                .findByStatusAndReservedUntilBefore(ReservationStatus.PENDING, Instant.now());
        for (Reservation reservation : overdue) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(reservation);
            eventPublisher.publish(reservation, "reservation.expired");
            log.info("Reservation {} expired", reservation.getId());
        }
    }

    private Reservation findOwned(UUID reservationId, UUID userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found: " + reservationId));
        if (!reservation.getUserId().equals(userId)) {
            throw new SecurityException("Access denied to reservation: " + reservationId);
        }
        return reservation;
    }

    private ReservationResponse toResponse(Reservation r) {
        return ReservationResponse.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .spotId(r.getSpotId())
                .vehiclePlate(r.getVehiclePlate())
                .status(r.getStatus())
                .reservedFrom(r.getReservedFrom())
                .reservedUntil(r.getReservedUntil())
                .checkedInAt(r.getCheckedInAt())
                .checkedOutAt(r.getCheckedOutAt())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
