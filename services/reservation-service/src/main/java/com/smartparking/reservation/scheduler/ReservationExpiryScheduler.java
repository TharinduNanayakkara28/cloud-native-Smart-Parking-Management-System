package com.smartparking.reservation.scheduler;

import com.smartparking.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationExpiryScheduler {

    private final ReservationService reservationService;

    /**
     * Every 60 seconds: find PENDING reservations whose reservedUntil has passed and expire them.
     * fixedDelay (not fixedRate) ensures the next run starts only after the previous one finishes,
     * preventing overlapping runs on slow DB queries.
     */
    @Scheduled(fixedDelay = 60_000)
    public void expireOverdueReservations() {
        log.debug("Running reservation expiry check");
        try {
            reservationService.expirePendingReservations();
        } catch (Exception e) {
            log.error("Reservation expiry job failed: {}", e.getMessage());
        }
    }
}
