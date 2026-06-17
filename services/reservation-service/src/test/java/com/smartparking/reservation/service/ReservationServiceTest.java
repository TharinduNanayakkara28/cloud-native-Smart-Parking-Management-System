package com.smartparking.reservation.service;

import com.smartparking.reservation.dto.CreateReservationRequest;
import com.smartparking.reservation.kafka.ReservationEventPublisher;
import com.smartparking.reservation.model.Reservation;
import com.smartparking.reservation.model.ReservationStatus;
import com.smartparking.reservation.repository.ReservationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock ReservationRepository reservationRepository;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock RedisScript<Long> releaseLockScript;
    @Mock ReservationEventPublisher eventPublisher;
    @Mock AvailabilityClient availabilityClient;

    @InjectMocks ReservationService reservationService;

    private final UUID userId = UUID.randomUUID();
    private final UUID spotId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void createReservation_freeSpotAndLockAvailable_createsAndPublishes() {
        when(availabilityClient.getSpotState(spotId)).thenReturn("FREE");
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        Reservation saved = buildReservation(ReservationStatus.PENDING);
        when(reservationRepository.save(any())).thenReturn(saved);
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(1L);

        CreateReservationRequest req = buildRequest();
        var response = reservationService.createReservation(userId, req);

        assertThat(response.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verify(eventPublisher).publish(any(), eq("reservation.created"));
    }

    @Test
    void createReservation_spotNotFree_throwsConflict() {
        when(availabilityClient.getSpotState(spotId)).thenReturn("OCCUPIED");

        assertThatThrownBy(() -> reservationService.createReservation(userId, buildRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not available");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservation_lockHeld_throwsConflict() {
        when(availabilityClient.getSpotState(spotId)).thenReturn("FREE");
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> reservationService.createReservation(userId, buildRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("being reserved");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void cancel_pendingReservation_cancelsAndPublishes() {
        Reservation reservation = buildReservation(ReservationStatus.PENDING);
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any())).thenReturn(reservation);

        var response = reservationService.cancel(reservation.getId(), userId);

        assertThat(response.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(eventPublisher).publish(any(), eq("reservation.cancelled"));
    }

    @Test
    void cancel_completedReservation_throwsConflict() {
        Reservation reservation = buildReservation(ReservationStatus.COMPLETED);
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancel(reservation.getId(), userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void checkin_pendingReservation_activatesAndPublishes() {
        Reservation reservation = buildReservation(ReservationStatus.PENDING);
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any())).thenReturn(reservation);

        var response = reservationService.checkin(reservation.getId(), userId);

        assertThat(response.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        verify(eventPublisher).publish(any(), eq("reservation.active"));
    }

    @Test
    void checkout_activeReservation_completesAndPublishes() {
        Reservation reservation = buildReservation(ReservationStatus.ACTIVE);
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any())).thenReturn(reservation);

        var response = reservationService.checkout(reservation.getId(), userId);

        assertThat(response.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        verify(eventPublisher).publish(any(), eq("reservation.completed"));
    }

    @Test
    void getReservation_wrongOwner_throwsSecurityException() {
        Reservation reservation = buildReservation(ReservationStatus.PENDING);
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        UUID differentUser = UUID.randomUUID();
        assertThatThrownBy(() -> reservationService.getReservation(reservation.getId(), differentUser))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void getReservation_notFound_throwsEntityNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(reservationRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.getReservation(unknownId, userId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void expirePendingReservations_expiresOverdueAndPublishes() {
        Reservation overdue = buildReservation(ReservationStatus.PENDING);
        when(reservationRepository.findByStatusAndReservedUntilBefore(eq(ReservationStatus.PENDING), any()))
                .thenReturn(List.of(overdue));
        when(reservationRepository.save(any())).thenReturn(overdue);

        reservationService.expirePendingReservations();

        assertThat(overdue.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        verify(eventPublisher).publish(any(), eq("reservation.expired"));
    }

    @Test
    void activateFromPayment_pendingReservation_activates() {
        Reservation reservation = buildReservation(ReservationStatus.PENDING);
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any())).thenReturn(reservation);

        reservationService.activateFromPayment(reservation.getId());

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        verify(eventPublisher).publish(any(), eq("reservation.active"));
    }

    private Reservation buildReservation(ReservationStatus status) {
        return Reservation.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .spotId(spotId)
                .vehiclePlate("ABC-001")
                .status(status)
                .reservedFrom(Instant.now())
                .reservedUntil(Instant.now().plusSeconds(3600))
                .build();
    }

    private CreateReservationRequest buildRequest() {
        CreateReservationRequest req = new CreateReservationRequest();
        req.setSpotId(spotId);
        req.setVehiclePlate("ABC-001");
        req.setReservedFrom(Instant.now());
        req.setReservedUntil(Instant.now().plusSeconds(7200));
        return req;
    }
}
