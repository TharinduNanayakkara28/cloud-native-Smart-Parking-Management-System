package com.smartparking.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class OccupancyResponse {
    private String date;
    private long totalReservations;
    private List<HourlyBucket> hourlyBreakdown;

    @Data
    @Builder
    public static class HourlyBucket {
        private Instant hour;
        private long reservationCount;
    }
}
