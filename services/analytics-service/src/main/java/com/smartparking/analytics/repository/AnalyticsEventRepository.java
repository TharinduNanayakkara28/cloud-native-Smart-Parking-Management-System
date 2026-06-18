package com.smartparking.analytics.repository;

import com.smartparking.analytics.model.AnalyticsEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, UUID> {

    // ── Raw event listing ──────────────────────────────────────────────────

    List<AnalyticsEvent> findByOrderByEventTimeDesc(Pageable pageable);

    List<AnalyticsEvent> findByEventTypeOrderByEventTimeDesc(String eventType, Pageable pageable);

    // ── Occupancy: hourly reservation.created counts for a date ───────────

    @Query(value = """
            SELECT date_trunc('hour', event_time) AS hour,
                   COUNT(*)                        AS reservations
            FROM analytics_events
            WHERE event_type = 'reservation.created'
              AND event_time >= :startOfDay
              AND event_time <  :endOfDay
            GROUP BY date_trunc('hour', event_time)
            ORDER BY date_trunc('hour', event_time)
            """, nativeQuery = true)
    List<Object[]> findHourlyReservationCounts(
            @Param("startOfDay") Instant startOfDay,
            @Param("endOfDay")   Instant endOfDay);

    @Query(value = """
            SELECT COUNT(*)
            FROM analytics_events
            WHERE event_type = 'reservation.created'
              AND event_time >= :startOfDay
              AND event_time <  :endOfDay
            """, nativeQuery = true)
    long countReservationsForDay(
            @Param("startOfDay") Instant startOfDay,
            @Param("endOfDay")   Instant endOfDay);

    // ── Revenue: payment amounts since periodStart ────────────────────────

    @Query(value = """
            SELECT
              COALESCE(SUM(CASE WHEN event_type = 'payment.success'  THEN amount ELSE 0 END), 0) AS gross,
              COALESCE(SUM(CASE WHEN event_type = 'payment.refunded' THEN amount ELSE 0 END), 0) AS refunded,
              COUNT(CASE WHEN event_type = 'payment.success' THEN 1 END)                         AS transaction_count
            FROM analytics_events
            WHERE event_type IN ('payment.success', 'payment.refunded')
              AND event_time >= :periodStart
            """, nativeQuery = true)
    Object[] findRevenueSummary(@Param("periodStart") Instant periodStart);

    // ── Violations: penalty.issued counts by tier ─────────────────────────

    @Query(value = """
            SELECT tier, COUNT(*) AS count
            FROM analytics_events
            WHERE event_type = 'penalty.issued'
            GROUP BY tier
            ORDER BY tier
            """, nativeQuery = true)
    List<Object[]> findViolationsByTier();
}
