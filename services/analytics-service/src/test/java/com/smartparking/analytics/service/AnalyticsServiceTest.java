package com.smartparking.analytics.service;

import com.smartparking.analytics.dto.OccupancyResponse;
import com.smartparking.analytics.dto.RevenueResponse;
import com.smartparking.analytics.dto.ViolationsResponse;
import com.smartparking.analytics.model.AnalyticsEvent;
import com.smartparking.analytics.repository.AnalyticsEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private AnalyticsEventRepository repository;
    @InjectMocks private AnalyticsService analyticsService;

    // ── record ────────────────────────────────────────────────────────────────

    @Test
    void record_persistsEventToRepository() {
        AnalyticsEvent event = AnalyticsEvent.builder()
                .eventType("reservation.created")
                .topic("reservation-events")
                .eventTime(Instant.now())
                .build();
        when(repository.save(any())).thenReturn(event);

        analyticsService.record(event);

        verify(repository).save(event);
    }

    // ── getOccupancy ──────────────────────────────────────────────────────────

    @Test
    void getOccupancy_returnsBucketsAndTotal() {
        Timestamp hour = Timestamp.from(Instant.parse("2026-06-18T09:00:00Z"));
        when(repository.findHourlyReservationCounts(any(), any()))
                .thenReturn(List.of(new Object[]{hour, 5L}));
        when(repository.countReservationsForDay(any(), any())).thenReturn(5L);

        OccupancyResponse response = analyticsService.getOccupancy("2026-06-18");

        assertThat(response.getDate()).isEqualTo("2026-06-18");
        assertThat(response.getTotalReservations()).isEqualTo(5L);
        assertThat(response.getHourlyBreakdown()).hasSize(1);
        assertThat(response.getHourlyBreakdown().get(0).getReservationCount()).isEqualTo(5L);
    }

    @Test
    void getOccupancy_returnsEmptyBreakdownWhenNoEvents() {
        when(repository.findHourlyReservationCounts(any(), any())).thenReturn(List.of());
        when(repository.countReservationsForDay(any(), any())).thenReturn(0L);

        OccupancyResponse response = analyticsService.getOccupancy("2026-06-18");

        assertThat(response.getTotalReservations()).isZero();
        assertThat(response.getHourlyBreakdown()).isEmpty();
    }

    @Test
    void getOccupancy_throwsForInvalidDateFormat() {
        assertThatThrownBy(() -> analyticsService.getOccupancy("not-a-date"))
                .isInstanceOf(Exception.class);
    }

    // ── getRevenue ────────────────────────────────────────────────────────────

    @Test
    void getRevenue_computesNetRevenue() {
        when(repository.findRevenueSummary(any()))
                .thenReturn(new Object[]{
                        new BigDecimal("100.00"),
                        new BigDecimal("15.00"),
                        10L
                });

        RevenueResponse response = analyticsService.getRevenue("week");

        assertThat(response.getGrossRevenue()).isEqualByComparingTo("100.00");
        assertThat(response.getRefundedAmount()).isEqualByComparingTo("15.00");
        assertThat(response.getNetRevenue()).isEqualByComparingTo("85.00");
        assertThat(response.getTransactionCount()).isEqualTo(10L);
        assertThat(response.getPeriod()).isEqualTo("week");
    }

    @Test
    void getRevenue_handlesNullAmountsAsZero() {
        when(repository.findRevenueSummary(any()))
                .thenReturn(new Object[]{null, null, 0L});

        RevenueResponse response = analyticsService.getRevenue("month");

        assertThat(response.getGrossRevenue()).isEqualByComparingTo("0");
        assertThat(response.getRefundedAmount()).isEqualByComparingTo("0");
        assertThat(response.getNetRevenue()).isEqualByComparingTo("0");
    }

    @Test
    void getRevenue_throwsForInvalidPeriod() {
        assertThatThrownBy(() -> analyticsService.getRevenue("quarter"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("period must be");
    }

    // ── getViolations ─────────────────────────────────────────────────────────

    @Test
    void getViolations_groupsByTierWithCorrectLabels() {
        when(repository.findViolationsByTier())
                .thenReturn(List.of(
                        new Object[]{1, 8L},
                        new Object[]{2, 3L},
                        new Object[]{3, 1L}
                ));

        ViolationsResponse response = analyticsService.getViolations();

        assertThat(response.getTotalViolations()).isEqualTo(12L);
        assertThat(response.getByTier()).hasSize(3);
        assertThat(response.getByTier().get(0).getType()).isEqualTo("WARNING");
        assertThat(response.getByTier().get(1).getType()).isEqualTo("FINE");
        assertThat(response.getByTier().get(2).getType()).isEqualTo("ESCALATED");
        assertThat(response.getByTier().get(0).getCount()).isEqualTo(8L);
    }

    @Test
    void getViolations_returnsZeroTotalWhenNoViolations() {
        when(repository.findViolationsByTier()).thenReturn(List.of());

        ViolationsResponse response = analyticsService.getViolations();

        assertThat(response.getTotalViolations()).isZero();
        assertThat(response.getByTier()).isEmpty();
    }

    // ── getEvents ─────────────────────────────────────────────────────────────

    @Test
    void getEvents_withoutTypeFilter_returnsAllRecent() {
        AnalyticsEvent e = AnalyticsEvent.builder()
                .eventType("reservation.created").topic("reservation-events")
                .eventTime(Instant.now()).build();
        when(repository.findByOrderByEventTimeDesc(any(Pageable.class))).thenReturn(List.of(e));

        var result = analyticsService.getEvents(null, 50);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventType()).isEqualTo("reservation.created");
    }

    @Test
    void getEvents_withTypeFilter_delegatesToFilteredQuery() {
        AnalyticsEvent e = AnalyticsEvent.builder()
                .eventType("payment.success").topic("payment-events")
                .eventTime(Instant.now()).build();
        when(repository.findByEventTypeOrderByEventTimeDesc(eq("payment.success"), any(Pageable.class)))
                .thenReturn(List.of(e));

        var result = analyticsService.getEvents("payment.success", 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventType()).isEqualTo("payment.success");
    }

    @Test
    void getEvents_capsLimitAt500() {
        when(repository.findByOrderByEventTimeDesc(any(Pageable.class))).thenReturn(List.of());

        analyticsService.getEvents(null, 9999);

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByOrderByEventTimeDesc(cap.capture());
        assertThat(cap.getValue().getPageSize()).isEqualTo(500);
    }
}
