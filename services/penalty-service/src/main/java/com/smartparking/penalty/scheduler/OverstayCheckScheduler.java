package com.smartparking.penalty.scheduler;

import com.smartparking.penalty.service.PenaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OverstayCheckScheduler {

    private final PenaltyService penaltyService;

    /**
     * Every 120 seconds: scan Redis for active sessions past their reservedUntil
     * and issue penalties for each elapsed tier.
     *
     * fixedDelay (not fixedRate) ensures runs don't overlap if Redis is slow.
     */
    @Scheduled(fixedDelay = 120_000)
    public void checkOverstays() {
        log.debug("Running overstay penalty check");
        try {
            penaltyService.checkAndIssueOverstayPenalties();
        } catch (Exception e) {
            log.error("Overstay check failed: {}", e.getMessage());
        }
    }
}
