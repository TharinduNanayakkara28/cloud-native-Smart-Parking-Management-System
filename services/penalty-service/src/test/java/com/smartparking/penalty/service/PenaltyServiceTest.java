package com.smartparking.penalty.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartparking.penalty.dto.OverstayRecord;
import com.smartparking.penalty.dto.PenaltyResponse;
import com.smartparking.penalty.dto.ReservationEventPayload;
import com.smartparking.penalty.kafka.PenaltyEventPublisher;
import com.smartparking.penalty.model.Penalty;
import com.smartparking.penalty.model.PenaltyStatus;
import com.smartparking.penalty.repository.PenaltyRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PenaltyServiceTest {

    @Mock private PenaltyRepository penaltyRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private PenaltyEventPublisher eventPublisher;
    @Mock private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @InjectMocks
    private PenaltyService penaltyService;

    private final UUID userId        = UUID.randomUUID();
    private final UUID reservationId = UUID.randomUUID();
    private final UUID spotId        = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(penaltyService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(penaltyService, "tier2Amount", new BigDecimal("10.00"));
        ReflectionTestUtils.setField(penaltyService, "tier3Amount", new BigDecimal("25.00"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── startOverstayTimer ────────────────────────────────────────────────────

    @Test
    void startOverstayTimer_storesRecordInRedis() {
        ReservationEventPayload payload = new ReservationEventPayload();
        payload.setReservationId(reservationId);
        payload.setUserId(userId);
        payload.setSpotId(spotId);
        payload.setReservedUntil(Instant.now().plusSeconds(3600));

        penaltyService.startOverstayTimer(payload);

        verify(valueOps).set(eq("overstay:" + reservationId), anyString());
    }

    // ── cancelOverstayTimer ───────────────────────────────────────────────────

    @Test
    void cancelOverstayTimer_deletesRedisKey() {
        penaltyService.cancelOverstayTimer(reservationId);

        verify(redisTemplate).delete("overstay:" + reservationId);
    }

    // ── tierAmount ────────────────────────────────────────────────────────────

    @Test
    void tierAmount_returnsCorrectAmounts() {
        assertThat(penaltyService.tierAmount(1)).isEqualByComparingTo("0.00");
        assertThat(penaltyService.tierAmount(2)).isEqualByComparingTo("10.00");
        assertThat(penaltyService.tierAmount(3)).isEqualByComparingTo("25.00");
    }

    // ── checkAndIssueOverstayPenalties ────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void checkOverstays_issueTier1WhenJustOverdue() throws Exception {
        Instant reservedUntil = Instant.now().minusSeconds(60 * 5); // 5 min overdue → Tier 1
        OverstayRecord record = OverstayRecord.builder()
                .reservationId(reservationId)
                .userId(userId)
                .spotId(spotId)
                .reservedUntil(reservedUntil)
                .lastTierIssued(0)
                .build();

        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("overstay:" + reservationId);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(valueOps.get("overstay:" + reservationId)).thenReturn(objectMapper.writeValueAsString(record));
        when(penaltyRepository.existsByReservationIdAndTierGreaterThanEqual(reservationId, 1)).thenReturn(false);
        when(penaltyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        penaltyService.checkAndIssueOverstayPenalties();

        ArgumentCaptor<Penalty> cap = ArgumentCaptor.forClass(Penalty.class);
        verify(penaltyRepository).save(cap.capture());
        assertThat(cap.getValue().getTier()).isEqualTo(1);
        assertThat(cap.getValue().getType()).isEqualTo("WARNING");
        assertThat(cap.getValue().getAmount()).isEqualByComparingTo("0.00");
        verify(eventPublisher).publish(any(Penalty.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkOverstays_issuesAllMissedTiersWhenJumpingToTier3() throws Exception {
        Instant reservedUntil = Instant.now().minusSeconds(60 * 70); // 70 min → Tier 3
        OverstayRecord record = OverstayRecord.builder()
                .reservationId(reservationId)
                .userId(userId)
                .spotId(spotId)
                .reservedUntil(reservedUntil)
                .lastTierIssued(0)
                .build();

        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("overstay:" + reservationId);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(valueOps.get("overstay:" + reservationId)).thenReturn(objectMapper.writeValueAsString(record));
        when(penaltyRepository.existsByReservationIdAndTierGreaterThanEqual(any(), anyInt())).thenReturn(false);
        when(penaltyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        penaltyService.checkAndIssueOverstayPenalties();

        verify(penaltyRepository, times(3)).save(any());
        verify(eventPublisher, times(3)).publish(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkOverstays_skipsSessionStillWithinWindow() throws Exception {
        OverstayRecord record = OverstayRecord.builder()
                .reservationId(reservationId)
                .userId(userId)
                .spotId(spotId)
                .reservedUntil(Instant.now().plusSeconds(3600))
                .lastTierIssued(0)
                .build();

        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("overstay:" + reservationId);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(valueOps.get("overstay:" + reservationId)).thenReturn(objectMapper.writeValueAsString(record));

        penaltyService.checkAndIssueOverstayPenalties();

        verifyNoInteractions(penaltyRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkOverstays_doesNotReissueTierAlreadyIssued() throws Exception {
        Instant reservedUntil = Instant.now().minusSeconds(60 * 5);
        OverstayRecord record = OverstayRecord.builder()
                .reservationId(reservationId)
                .userId(userId)
                .spotId(spotId)
                .reservedUntil(reservedUntil)
                .lastTierIssued(1) // Tier 1 already issued
                .build();

        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("overstay:" + reservationId);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(valueOps.get("overstay:" + reservationId)).thenReturn(objectMapper.writeValueAsString(record));

        penaltyService.checkAndIssueOverstayPenalties();

        verifyNoInteractions(penaltyRepository);
        verifyNoInteractions(eventPublisher);
    }

    // ── getMyPenalties ────────────────────────────────────────────────────────

    @Test
    void getMyPenalties_returnsListForUser() {
        Penalty p = Penalty.builder()
                .id(UUID.randomUUID())
                .reservationId(reservationId)
                .userId(userId)
                .spotId(spotId)
                .type("WARNING")
                .tier(1)
                .amount(BigDecimal.ZERO)
                .status(PenaltyStatus.ISSUED)
                .build();
        when(penaltyRepository.findByUserId(userId)).thenReturn(List.of(p));

        List<PenaltyResponse> result = penaltyService.getMyPenalties(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTier()).isEqualTo(1);
    }

    // ── pay ───────────────────────────────────────────────────────────────────

    @Test
    void pay_transitionsPenaltyToPaid() {
        UUID penaltyId = UUID.randomUUID();
        Penalty p = Penalty.builder()
                .id(penaltyId)
                .userId(userId)
                .reservationId(reservationId)
                .spotId(spotId)
                .type("FINE")
                .tier(2)
                .amount(new BigDecimal("10.00"))
                .status(PenaltyStatus.ISSUED)
                .build();
        when(penaltyRepository.findById(penaltyId)).thenReturn(Optional.of(p));
        when(penaltyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PenaltyResponse response = penaltyService.pay(penaltyId, userId);

        assertThat(response.getStatus()).isEqualTo(PenaltyStatus.PAID);
        assertThat(response.getPaidAt()).isNotNull();
    }

    @Test
    void pay_throwsForbiddenForWrongUser() {
        UUID penaltyId = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        Penalty p = Penalty.builder()
                .id(penaltyId)
                .userId(userId)
                .status(PenaltyStatus.ISSUED)
                .build();
        when(penaltyRepository.findById(penaltyId)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> penaltyService.pay(penaltyId, otherUser))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void pay_throwsConflictWhenAlreadyPaid() {
        UUID penaltyId = UUID.randomUUID();
        Penalty p = Penalty.builder()
                .id(penaltyId)
                .userId(userId)
                .status(PenaltyStatus.PAID)
                .build();
        when(penaltyRepository.findById(penaltyId)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> penaltyService.pay(penaltyId, userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pay_throwsNotFoundForMissingPenalty() {
        UUID penaltyId = UUID.randomUUID();
        when(penaltyRepository.findById(penaltyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> penaltyService.pay(penaltyId, userId))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
