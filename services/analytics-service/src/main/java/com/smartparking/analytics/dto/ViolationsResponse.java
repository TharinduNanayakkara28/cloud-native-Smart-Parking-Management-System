package com.smartparking.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ViolationsResponse {
    private long totalViolations;
    private List<TierBreakdown> byTier;

    @Data
    @Builder
    public static class TierBreakdown {
        private int tier;
        private String type;
        private long count;
    }
}
