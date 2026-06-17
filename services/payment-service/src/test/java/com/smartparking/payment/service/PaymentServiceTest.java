package com.smartparking.payment.service;

import com.smartparking.payment.dto.ReservationEventPayload;
import com.smartparking.payment.kafka.PaymentEventPublisher;
import com.smartparking.payment.model.Payment;
import com.smartparking.payment.model.PaymentStatus;
import com.smartparking.payment.provider.PaymentProvider;
import com.smartparking.payment.provider.PaymentProviderException;
import com.smartparking.payment.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentProvider paymentProvider;
    @Mock PaymentEventPublisher eventPublisher;

    @InjectMocks PaymentService paymentService;

    private final UUID reservationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "hourlyRate", new BigDecimal("2.00"));
        ReflectionTestUtils.setField(paymentService, "currency", "USD");
    }

    // ── preAuthorise ──────────────────────────────────────────────────────────

    @Test
    void preAuthorise_newReservation_holdsAndPublishesSuccess() {
        when(paymentRepository.existsByIdempotencyKey(any())).thenReturn(false);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentProvider.hold(any(), any())).thenReturn("mock-ref-123");

        paymentService.preAuthorise(buildPayload(2));

        verify(paymentProvider).hold(new BigDecimal("4.00"), reservationId + ":reservation.created");
        verify(eventPublisher).publish(argThat(p -> p.getStatus() == PaymentStatus.HELD), eq("payment.success"));
    }

    @Test
    void preAuthorise_idempotentKey_skipsProcessing() {
        when(paymentRepository.existsByIdempotencyKey(any())).thenReturn(true);

        paymentService.preAuthorise(buildPayload(1));

        verify(paymentRepository, never()).save(any());
        verify(paymentProvider, never()).hold(any(), any());
    }

    @Test
    void preAuthorise_providerRejects_publishesFailure() {
        when(paymentRepository.existsByIdempotencyKey(any())).thenReturn(false);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentProvider.hold(any(), any())).thenThrow(new PaymentProviderException("declined"));

        paymentService.preAuthorise(buildPayload(1));

        verify(eventPublisher).publish(argThat(p -> p.getStatus() == PaymentStatus.FAILED), eq("payment.failed"));
    }

    // ── captureActual ─────────────────────────────────────────────────────────

    @Test
    void captureActual_heldPayment_capturesActualDuration() {
        Payment payment = buildPayment(PaymentStatus.HELD, new BigDecimal("4.00"));
        when(paymentRepository.findByReservationId(reservationId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReservationEventPayload payload = buildPayload(2);
        Instant checkin  = Instant.now().minus(90, ChronoUnit.MINUTES);
        Instant checkout = Instant.now();
        payload.setCheckedInAt(checkin);
        payload.setCheckedOutAt(checkout);

        paymentService.captureActual(payload);

        // 90 min → ceil(1.5) = 2 hours → $4.00
        verify(paymentProvider).capture(eq("mock-ref"), eq(new BigDecimal("4.00")));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CHARGED);
    }

    @Test
    void captureActual_noHeldPayment_isNoOp() {
        when(paymentRepository.findByReservationId(reservationId))
                .thenReturn(Optional.of(buildPayment(PaymentStatus.CHARGED, new BigDecimal("2.00"))));

        paymentService.captureActual(buildPayload(1));

        verify(paymentProvider, never()).capture(any(), any());
    }

    // ── refund ────────────────────────────────────────────────────────────────

    @Test
    void refund_heldPayment_refundsAndPublishes() {
        Payment payment = buildPayment(PaymentStatus.HELD, new BigDecimal("4.00"));
        when(paymentRepository.findByReservationId(reservationId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.refund(buildPayload(2));

        verify(paymentProvider).refund("mock-ref");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(eventPublisher).publish(any(), eq("payment.refunded"));
    }

    @Test
    void refund_alreadyRefunded_isNoOp() {
        when(paymentRepository.findByReservationId(reservationId))
                .thenReturn(Optional.of(buildPayment(PaymentStatus.REFUNDED, new BigDecimal("2.00"))));

        paymentService.refund(buildPayload(1));

        verify(paymentProvider, never()).refund(any());
    }

    // ── getByReservationId ────────────────────────────────────────────────────

    @Test
    void getByReservationId_correctOwner_returnsReceipt() {
        when(paymentRepository.findByReservationId(reservationId))
                .thenReturn(Optional.of(buildPayment(PaymentStatus.HELD, new BigDecimal("2.00"))));

        var response = paymentService.getByReservationId(reservationId, userId);

        assertThat(response.getReservationId()).isEqualTo(reservationId);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.HELD);
    }

    @Test
    void getByReservationId_notFound_throwsEntityNotFoundException() {
        when(paymentRepository.findByReservationId(reservationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getByReservationId(reservationId, userId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getByReservationId_wrongOwner_throwsSecurityException() {
        when(paymentRepository.findByReservationId(reservationId))
                .thenReturn(Optional.of(buildPayment(PaymentStatus.HELD, new BigDecimal("2.00"))));

        assertThatThrownBy(() -> paymentService.getByReservationId(reservationId, UUID.randomUUID()))
                .isInstanceOf(SecurityException.class);
    }

    // ── pricing ───────────────────────────────────────────────────────────────

    @Test
    void calculateAmount_exactHour_chargesOneHour() {
        Instant from  = Instant.parse("2026-06-17T10:00:00Z");
        Instant until = Instant.parse("2026-06-17T11:00:00Z");
        assertThat(paymentService.calculateAmount(from, until)).isEqualByComparingTo("2.00");
    }

    @Test
    void calculateAmount_partialHour_roundsUp() {
        Instant from  = Instant.parse("2026-06-17T10:00:00Z");
        Instant until = Instant.parse("2026-06-17T10:30:00Z");
        assertThat(paymentService.calculateAmount(from, until)).isEqualByComparingTo("2.00");
    }

    @Test
    void calculateAmount_twoAndHalfHours_chargesThreeHours() {
        Instant from  = Instant.parse("2026-06-17T10:00:00Z");
        Instant until = Instant.parse("2026-06-17T12:30:00Z");
        assertThat(paymentService.calculateAmount(from, until)).isEqualByComparingTo("6.00");
    }

    @Test
    void calculateAmount_nullTimes_returnsHourlyRate() {
        assertThat(paymentService.calculateAmount(null, null)).isEqualByComparingTo("2.00");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ReservationEventPayload buildPayload(int durationHours) {
        ReservationEventPayload p = new ReservationEventPayload();
        p.setReservationId(reservationId);
        p.setUserId(userId);
        p.setReservedFrom(Instant.now());
        p.setReservedUntil(Instant.now().plus(durationHours, ChronoUnit.HOURS));
        return p;
    }

    private Payment buildPayment(PaymentStatus status, BigDecimal amount) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .reservationId(reservationId)
                .userId(userId)
                .amount(amount)
                .currency("USD")
                .status(status)
                .providerRef("mock-ref")
                .idempotencyKey(reservationId + ":reservation.created")
                .build();
    }
}
