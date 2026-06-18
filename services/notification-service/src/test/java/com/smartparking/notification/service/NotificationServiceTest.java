package com.smartparking.notification.service;

import com.smartparking.notification.dto.*;
import com.smartparking.notification.model.Notification;
import com.smartparking.notification.provider.NotificationProvider;
import com.smartparking.notification.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationProvider notificationProvider;
    @InjectMocks private NotificationService notificationService;

    private final UUID userId = UUID.randomUUID();
    private final UUID reservationId = UUID.randomUUID();

    // ── Reservation events ────────────────────────────────────────────────────

    @Test
    void handleReservationCreated_savesConfirmedNotification() {
        ReservationEvent event = buildReservationEvent("reservation.created");
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.handleReservationEvent(event);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo("RESERVATION_CREATED");
        assertThat(cap.getValue().getTitle()).isEqualTo("Reservation Confirmed");
        assertThat(cap.getValue().getUserId()).isEqualTo(userId);
        verify(notificationProvider).deliver(any());
    }

    @Test
    void handleReservationExpired_savesExpiredNotification() {
        ReservationEvent event = buildReservationEvent("reservation.expired");
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.handleReservationEvent(event);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo("RESERVATION_EXPIRED");
    }

    @Test
    void handleReservationEvent_ignoresUnknownEventType() {
        ReservationEvent event = buildReservationEvent("reservation.unknown");

        notificationService.handleReservationEvent(event);

        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(notificationProvider);
    }

    @Test
    void handleReservationEvent_skipsNullPayload() {
        ReservationEvent event = new ReservationEvent();
        event.setEventType("reservation.created");

        notificationService.handleReservationEvent(event);

        verifyNoInteractions(notificationRepository);
    }

    // ── Payment events ────────────────────────────────────────────────────────

    @Test
    void handlePaymentSuccess_savesPaymentNotification() {
        PaymentEvent event = buildPaymentEvent("payment.success", "12.50");
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.handlePaymentEvent(event);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo("PAYMENT_SUCCESS");
        assertThat(cap.getValue().getMessage()).contains("12.50");
    }

    @Test
    void handlePaymentFailed_savesFailedNotification() {
        PaymentEvent event = buildPaymentEvent("payment.failed", "0.00");
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.handlePaymentEvent(event);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    void handlePaymentRefunded_savesRefundNotification() {
        PaymentEvent event = buildPaymentEvent("payment.refunded", "10.00");
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.handlePaymentEvent(event);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo("PAYMENT_REFUNDED");
    }

    // ── Penalty events ────────────────────────────────────────────────────────

    @Test
    void handlePenaltyTier1_savesWarningNotification() {
        PenaltyEvent event = buildPenaltyEvent(1, "0.00");
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.handlePenaltyEvent(event);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        assertThat(cap.getValue().getTitle()).isEqualTo("Overstay Warning");
        assertThat(cap.getValue().getType()).isEqualTo("PENALTY_ISSUED");
    }

    @Test
    void handlePenaltyTier2_savesFineNotification() {
        PenaltyEvent event = buildPenaltyEvent(2, "10.00");
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.handlePenaltyEvent(event);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        assertThat(cap.getValue().getTitle()).isEqualTo("Overstay Fine Issued");
        assertThat(cap.getValue().getMessage()).contains("10.00").contains("Tier 2");
    }

    // ── REST operations ───────────────────────────────────────────────────────

    @Test
    void markRead_transitionsToRead() {
        UUID notifId = UUID.randomUUID();
        Notification n = Notification.builder()
                .id(notifId).userId(userId).type("PAYMENT_SUCCESS")
                .title("t").message("m").channel("IN_APP").read(false).build();
        when(notificationRepository.findById(notifId)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = notificationService.markRead(notifId, userId);

        assertThat(response.isRead()).isTrue();
        assertThat(response.getReadAt()).isNotNull();
    }

    @Test
    void markRead_throwsForbiddenForWrongUser() {
        UUID notifId = UUID.randomUUID();
        Notification n = Notification.builder()
                .id(notifId).userId(UUID.randomUUID()).build();
        when(notificationRepository.findById(notifId)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> notificationService.markRead(notifId, userId))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void markRead_throwsNotFoundForMissingNotification() {
        UUID notifId = UUID.randomUUID();
        when(notificationRepository.findById(notifId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(notifId, userId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getMyNotifications_returnsUserNotifications() {
        Notification n = Notification.builder()
                .id(UUID.randomUUID()).userId(userId).type("RESERVATION_CREATED")
                .title("t").message("m").channel("IN_APP").read(false).build();
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(n));

        var result = notificationService.getMyNotifications(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo("RESERVATION_CREATED");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ReservationEvent buildReservationEvent(String type) {
        ReservationEventPayload payload = new ReservationEventPayload();
        payload.setReservationId(reservationId);
        payload.setUserId(userId);
        payload.setReservedUntil(Instant.now().plusSeconds(3600));

        ReservationEvent event = new ReservationEvent();
        event.setEventType(type);
        event.setTimestamp(Instant.now());
        event.setPayload(payload);
        return event;
    }

    private PaymentEvent buildPaymentEvent(String type, String amount) {
        PaymentEventPayload payload = new PaymentEventPayload();
        payload.setUserId(userId.toString());
        payload.setReservationId(reservationId.toString());
        payload.setAmount(new BigDecimal(amount));
        payload.setCurrency("USD");

        PaymentEvent event = new PaymentEvent();
        event.setEventType(type);
        event.setTimestamp(Instant.now());
        event.setPayload(payload);
        return event;
    }

    private PenaltyEvent buildPenaltyEvent(int tier, String amount) {
        PenaltyEventPayload payload = new PenaltyEventPayload();
        payload.setUserId(userId);
        payload.setReservationId(reservationId);
        payload.setSpotId(UUID.randomUUID());
        payload.setTier(tier);
        payload.setAmount(new BigDecimal(amount));
        payload.setType(tier == 1 ? "WARNING" : "FINE");

        PenaltyEvent event = new PenaltyEvent();
        event.setEventType("penalty.issued");
        event.setTimestamp(Instant.now());
        event.setPayload(payload);
        return event;
    }
}
