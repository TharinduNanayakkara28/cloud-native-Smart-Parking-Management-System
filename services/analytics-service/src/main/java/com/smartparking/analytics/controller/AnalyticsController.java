package com.smartparking.analytics.controller;

import com.smartparking.analytics.dto.*;
import com.smartparking.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Hourly reservation counts for a specific date.
     * Example: GET /analytics/occupancy?date=2026-06-18
     */
    @GetMapping("/occupancy")
    public ResponseEntity<OccupancyResponse> getOccupancy(
            @RequestParam String date) {
        return ResponseEntity.ok(analyticsService.getOccupancy(date));
    }

    /**
     * Revenue summary for the last week or month.
     * Example: GET /analytics/revenue?period=week
     */
    @GetMapping("/revenue")
    public ResponseEntity<RevenueResponse> getRevenue(
            @RequestParam(defaultValue = "week") String period) {
        return ResponseEntity.ok(analyticsService.getRevenue(period));
    }

    /**
     * Penalty / violation counts grouped by tier.
     * Example: GET /analytics/violations
     */
    @GetMapping("/violations")
    public ResponseEntity<ViolationsResponse> getViolations() {
        return ResponseEntity.ok(analyticsService.getViolations());
    }

    /**
     * Raw event listing for operator debugging.
     * Example: GET /analytics/events?type=payment.success&limit=20
     */
    @GetMapping("/events")
    public ResponseEntity<List<EventRecordResponse>> getEvents(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(analyticsService.getEvents(type, limit));
    }
}
