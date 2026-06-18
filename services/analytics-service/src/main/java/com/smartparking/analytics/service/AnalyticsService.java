package com.smartparking.analytics.service;

import com.smartparking.analytics.dto.*;
import com.smartparking.analytics.model.AnalyticsEvent;
import com.smartparking.analytics.repository.AnalyticsEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private static final Map<Integer, String> TIER_LABELS = Map.of(
            1, "WARNING", 2, "FINE", 3, "ESCALATED");

    private final AnalyticsEventRepository repository;

    // ── Event ingestion ───────────────────────────────────────────────────────

    @Transactional
    public void record(AnalyticsEvent event) {
        repository.save(event);
    }

    // ── Occupancy ─────────────────────────────────────────────────────────────

    public OccupancyResponse getOccupancy(String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        Instant startOfDay = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay   = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Object[]> rows = repository.findHourlyReservationCounts(startOfDay, endOfDay);
        long total = repository.countReservationsForDay(startOfDay, endOfDay);

        List<OccupancyResponse.HourlyBucket> breakdown = rows.stream()
                .map(row -> OccupancyResponse.HourlyBucket.builder()
                        .hour(((java.sql.Timestamp) row[0]).toInstant())
                        .reservationCount(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());

        return OccupancyResponse.builder()
                .date(dateStr)
                .totalReservations(total)
                .hourlyBreakdown(breakdown)
                .build();
    }

    // ── Revenue ───────────────────────────────────────────────────────────────

    public RevenueResponse getRevenue(String period) {
        Instant periodStart = resolvePeriodStart(period);
        Object[] row = repository.findRevenueSummary(periodStart);

        BigDecimal gross    = toBigDecimal(row[0]);
        BigDecimal refunded = toBigDecimal(row[1]);
        long txCount        = ((Number) row[2]).longValue();

        return RevenueResponse.builder()
                .period(period)
                .periodStart(periodStart)
                .grossRevenue(gross)
                .refundedAmount(refunded)
                .netRevenue(gross.subtract(refunded))
                .transactionCount(txCount)
                .build();
    }

    // ── Violations ────────────────────────────────────────────────────────────

    public ViolationsResponse getViolations() {
        List<Object[]> rows = repository.findViolationsByTier();

        List<ViolationsResponse.TierBreakdown> breakdown = rows.stream()
                .map(row -> {
                    int tier  = ((Number) row[0]).intValue();
                    long count = ((Number) row[1]).longValue();
                    return ViolationsResponse.TierBreakdown.builder()
                            .tier(tier)
                            .type(TIER_LABELS.getOrDefault(tier, "UNKNOWN"))
                            .count(count)
                            .build();
                })
                .collect(Collectors.toList());

        long total = breakdown.stream().mapToLong(ViolationsResponse.TierBreakdown::getCount).sum();

        return ViolationsResponse.builder()
                .totalViolations(total)
                .byTier(breakdown)
                .build();
    }

    // ── Raw event listing ─────────────────────────────────────────────────────

    public List<EventRecordResponse> getEvents(String eventType, int limit) {
        int cappedLimit = Math.min(limit, 500);
        List<AnalyticsEvent> events = eventType != null && !eventType.isBlank()
                ? repository.findByEventTypeOrderByEventTimeDesc(eventType, PageRequest.of(0, cappedLimit))
                : repository.findByOrderByEventTimeDesc(PageRequest.of(0, cappedLimit));

        return events.stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Instant resolvePeriodStart(String period) {
        return switch (period.toLowerCase()) {
            case "week"  -> Instant.now().minusSeconds(7L * 24 * 3600);
            case "month" -> Instant.now().minusSeconds(30L * 24 * 3600);
            default      -> throw new IllegalArgumentException("period must be 'week' or 'month', got: " + period);
        };
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO.setScale(2);
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }

    private EventRecordResponse toResponse(AnalyticsEvent e) {
        return EventRecordResponse.builder()
                .id(e.getId())
                .eventType(e.getEventType())
                .topic(e.getTopic())
                .userId(e.getUserId())
                .entityId(e.getEntityId())
                .amount(e.getAmount())
                .tier(e.getTier())
                .eventTime(e.getEventTime())
                .receivedAt(e.getReceivedAt())
                .build();
    }
}
