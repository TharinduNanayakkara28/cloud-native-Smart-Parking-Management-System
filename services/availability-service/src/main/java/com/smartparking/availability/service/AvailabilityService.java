package com.smartparking.availability.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.availability.dto.*;
import com.smartparking.availability.model.AvailabilitySpot;
import com.smartparking.availability.repository.AvailabilitySpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvailabilityService {

    static final String SPOT_STATE_KEY    = "spot:%s:state";
    static final String FULL_MAP_CACHE_KEY = "availability:full-map";
    static final String WS_TOPIC           = "/topic/spots";

    private final AvailabilitySpotRepository spotRepository;
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public void processStateChanged(SpotStateChangedEvent event) {
        SpotStateChangedPayload payload = event.getPayload();
        String spotId = payload.getSpotId();

        // 1. Update individual spot state in Redis
        try {
            redisTemplate.opsForValue()
                    .set(String.format(SPOT_STATE_KEY, spotId), payload.getNewState());
            // Evict burst cache so next GET /spots/state is fresh
            redisTemplate.delete(FULL_MAP_CACHE_KEY);
        } catch (Exception e) {
            log.error("Redis update failed for spot {}: {}", spotId, e.getMessage());
        }

        // 2. Broadcast real-time update to WebSocket subscribers
        SpotStateUpdate wsMessage = SpotStateUpdate.builder()
                .spotId(spotId)
                .spotNumber(payload.getSpotNumber())
                .lotId(payload.getLotId())
                .previousState(payload.getPreviousState())
                .newState(payload.getNewState())
                .latitude(payload.getLatitude())
                .longitude(payload.getLongitude())
                .timestamp(Instant.now())
                .build();
        try {
            messagingTemplate.convertAndSend(WS_TOPIC, wsMessage);
        } catch (Exception e) {
            log.error("WebSocket broadcast failed for spot {}: {}", spotId, e.getMessage());
        }

        log.info("Availability updated: spot {} → {}", payload.getSpotNumber(), payload.getNewState());
    }

    /**
     * Returns spots within radiusMeters of (lat, lng) that are currently FREE.
     * PostGIS handles the spatial filter; Redis provides the state.
     */
    public List<AvailableSpotResponse> getAvailableSpots(double lat, double lng, double radiusMeters) {
        List<AvailabilitySpot> candidates = spotRepository.findWithinRadius(lat, lng, radiusMeters);

        // Batch-fetch states from Redis using pipeline
        List<String> keys = candidates.stream()
                .map(s -> String.format(SPOT_STATE_KEY, s.getId()))
                .collect(Collectors.toList());

        List<String> states = redisTemplate.opsForValue().multiGet(keys);

        return buildFreeSpots(candidates, states);
    }

    /**
     * Returns all spots with their current state, backed by a 5-second Redis cache.
     */
    public List<AvailableSpotResponse> getAllSpotStates() {
        String cached = redisTemplate.opsForValue().get(FULL_MAP_CACHE_KEY);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Failed to deserialize full-map cache, rebuilding: {}", e.getMessage());
            }
        }

        List<AvailabilitySpot> all = spotRepository.findAll();
        List<String> keys = all.stream()
                .map(s -> String.format(SPOT_STATE_KEY, s.getId()))
                .collect(Collectors.toList());
        List<String> states = redisTemplate.opsForValue().multiGet(keys);

        List<AvailableSpotResponse> result = buildAllSpots(all, states);

        try {
            redisTemplate.opsForValue()
                    .set(FULL_MAP_CACHE_KEY, objectMapper.writeValueAsString(result),
                            Duration.ofSeconds(5));
        } catch (Exception e) {
            log.warn("Failed to cache full-map: {}", e.getMessage());
        }

        return result;
    }

    public String getSpotState(String spotId) {
        String state = redisTemplate.opsForValue().get(String.format(SPOT_STATE_KEY, spotId));
        return state != null ? state : "FREE";
    }

    public void markSpotState(String spotId, String newState) {
        try {
            redisTemplate.opsForValue().set(String.format(SPOT_STATE_KEY, spotId), newState);
            redisTemplate.delete(FULL_MAP_CACHE_KEY);
        } catch (Exception e) {
            log.error("Redis update failed for spot {} → {}: {}", spotId, newState, e.getMessage());
        }
    }

    private List<AvailableSpotResponse> buildFreeSpots(List<AvailabilitySpot> spots, List<String> states) {
        return buildResponses(spots, states, true);
    }

    private List<AvailableSpotResponse> buildAllSpots(List<AvailabilitySpot> spots, List<String> states) {
        return buildResponses(spots, states, false);
    }

    private List<AvailableSpotResponse> buildResponses(List<AvailabilitySpot> spots,
                                                        List<String> states,
                                                        boolean freeOnly) {
        var result = new java.util.ArrayList<AvailableSpotResponse>();
        for (int i = 0; i < spots.size(); i++) {
            AvailabilitySpot spot = spots.get(i);
            // Default to FREE if Redis has no entry yet (spot not yet seen by any event)
            String state = (states != null && i < states.size() && states.get(i) != null)
                    ? states.get(i) : "FREE";
            if (freeOnly && !"FREE".equals(state)) continue;
            result.add(AvailableSpotResponse.builder()
                    .id(spot.getId())
                    .lotId(spot.getLotId())
                    .spotNumber(spot.getSpotNumber())
                    .floor(spot.getFloor())
                    .latitude(spot.getLatitude())
                    .longitude(spot.getLongitude())
                    .state(state)
                    .build());
        }
        return result;
    }
}
