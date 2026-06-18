package com.smartparking.penalty.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.penalty.dto.OverstayRecord;
import com.smartparking.penalty.dto.PenaltyResponse;
import com.smartparking.penalty.dto.ReservationEventPayload;
import com.smartparking.penalty.kafka.PenaltyEventPublisher;
import com.smartparking.penalty.model.Penalty;
import com.smartparking.penalty.model.PenaltyStatus;
import com.smartparking.penalty.repository.PenaltyRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PenaltyService {

    static final String OVERSTAY_KEY_PREFIX = "overstay:";

    private final PenaltyRepository penaltyRepository;
    private final StringRedisTemplate redisTemplate;
    private final PenaltyEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${penalty.tier2-amount:10.00}")
    private BigDecimal tier2Amount;

    @Value("${penalty.tier3-amount:25.00}")
    private BigDecimal tier3Amount;

    // ── Overstay timer management ─────────────────────────────────────────────

    public void startOverstayTimer(ReservationEventPayload payload) {
        OverstayRecord record = OverstayRecord.builder()
                .reservationId(payload.getReservationId())
                .userId(payload.getUserId())
                .spotId(payload.getSpotId())
                .reservedUntil(payload.getReservedUntil())
                .lastTierIssued(0)
                .build();
        writeRecord(record);
        log.info("Overstay timer started for reservation {} (until {})",
                payload.getReservationId(), payload.getReservedUntil());
    }

    public void cancelOverstayTimer(UUID reservationId) {
        redisTemplate.delete(OVERSTAY_KEY_PREFIX + reservationId);
        log.info("Overstay timer cancelled for reservation {}", reservationId);
    }

    // ── Scheduled check (called by OverstayCheckScheduler) ───────────────────

    @Transactional
    public void checkAndIssueOverstayPenalties() {
        ScanOptions options = ScanOptions.scanOptions()
                .match(OVERSTAY_KEY_PREFIX + "*")
                .count(100)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                try {
                    processKey(key);
                } catch (Exception e) {
                    log.error("Error processing overstay key {}: {}", key, e.getMessage());
                }
            }
        }
    }

    private void processKey(String key) throws Exception {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return;

        OverstayRecord record = objectMapper.readValue(json, OverstayRecord.class);
        if (!record.getReservedUntil().isBefore(Instant.now())) return;

        long overdueMinutes = Duration.between(record.getReservedUntil(), Instant.now()).toMinutes();
        int newTier = determineTier(overdueMinutes);
        int lastTier = record.getLastTierIssued();

        if (newTier <= lastTier) return;

        for (int tier = lastTier + 1; tier <= newTier; tier++) {
            issuePenalty(record, tier);
        }

        record.setLastTierIssued(newTier);
        writeRecord(record);
    }

    private void issuePenalty(OverstayRecord record, int tier) {
        // DB-level guard against duplicate issuance (e.g., after service restart)
        if (penaltyRepository.existsByReservationIdAndTierGreaterThanEqual(record.getReservationId(), tier)) {
            log.debug("Penalty tier {} already exists for reservation {}, skipping", tier, record.getReservationId());
            return;
        }

        Penalty penalty = Penalty.builder()
                .reservationId(record.getReservationId())
                .userId(record.getUserId())
                .spotId(record.getSpotId())
                .type(tierType(tier))
                .tier(tier)
                .amount(tierAmount(tier))
                .status(PenaltyStatus.ISSUED)
                .build();
        penaltyRepository.save(penalty);
        eventPublisher.publish(penalty);
        log.info("Issued tier {} ({}) penalty ${} for reservation {}",
                tier, tierType(tier), tierAmount(tier), record.getReservationId());
    }

    // ── REST operations ───────────────────────────────────────────────────────

    public List<PenaltyResponse> getMyPenalties(UUID userId) {
        return penaltyRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PenaltyResponse pay(UUID penaltyId, UUID userId) {
        Penalty penalty = penaltyRepository.findById(penaltyId)
                .orElseThrow(() -> new EntityNotFoundException("Penalty not found: " + penaltyId));
        if (!penalty.getUserId().equals(userId)) {
            throw new SecurityException("Access denied to penalty: " + penaltyId);
        }
        if (penalty.getStatus() != PenaltyStatus.ISSUED) {
            throw new IllegalStateException("Penalty is already " + penalty.getStatus());
        }
        penalty.setStatus(PenaltyStatus.PAID);
        penalty.setPaidAt(Instant.now());
        penaltyRepository.save(penalty);
        log.info("Penalty {} paid by user {}", penaltyId, userId);
        return toResponse(penalty);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private int determineTier(long overdueMinutes) {
        if (overdueMinutes >= 60) return 3;
        if (overdueMinutes >= 15) return 2;
        return 1;
    }

    private String tierType(int tier) {
        return switch (tier) {
            case 1 -> "WARNING";
            case 2 -> "FINE";
            default -> "ESCALATED";
        };
    }

    BigDecimal tierAmount(int tier) {
        return switch (tier) {
            case 1 -> BigDecimal.ZERO.setScale(2);
            case 2 -> tier2Amount;
            default -> tier3Amount;
        };
    }

    private void writeRecord(OverstayRecord record) {
        try {
            String key = OVERSTAY_KEY_PREFIX + record.getReservationId();
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(record));
            redisTemplate.expire(key, Duration.ofHours(24));
        } catch (Exception e) {
            log.error("Failed to write overstay record for {}: {}", record.getReservationId(), e.getMessage());
        }
    }

    private PenaltyResponse toResponse(Penalty p) {
        return PenaltyResponse.builder()
                .id(p.getId())
                .reservationId(p.getReservationId())
                .userId(p.getUserId())
                .spotId(p.getSpotId())
                .type(p.getType())
                .tier(p.getTier())
                .amount(p.getAmount())
                .status(p.getStatus())
                .issuedAt(p.getIssuedAt())
                .paidAt(p.getPaidAt())
                .build();
    }
}
