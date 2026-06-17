package com.smartparking.availability.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartparking.availability.dto.*;
import com.smartparking.availability.model.AvailabilitySpot;
import com.smartparking.availability.repository.AvailabilitySpotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @Mock AvailabilitySpotRepository spotRepository;
    @Mock StringRedisTemplate redisTemplate;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks AvailabilityService availabilityService;

    private UUID spotId;
    private AvailabilitySpot testSpot;

    @BeforeEach
    void setUp() {
        // Wire @InjectMocks with real ObjectMapper (not a mock)
        availabilityService = new AvailabilityService(
                spotRepository, redisTemplate, messagingTemplate,
                new ObjectMapper().registerModule(new JavaTimeModule()));

        spotId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        testSpot = AvailabilitySpot.builder()
                .id(spotId)
                .lotId(UUID.fromString("00000000-0000-0000-0000-000000000100"))
                .spotNumber("A1")
                .floor(1)
                .latitude(6.92710)
                .longitude(79.86100)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void processStateChanged_updatesRedisAndBroadcasts() {
        SpotStateChangedEvent event = buildEvent(spotId.toString(), "A1", "FREE", "OCCUPIED");

        availabilityService.processStateChanged(event);

        verify(valueOps).set("spot:" + spotId + ":state", "OCCUPIED");
        verify(redisTemplate).delete(AvailabilityService.FULL_MAP_CACHE_KEY);
        verify(messagingTemplate).convertAndSend(eq(AvailabilityService.WS_TOPIC), any(SpotStateUpdate.class));
    }

    @Test
    void processStateChanged_broadcastsCorrectPayload() {
        SpotStateChangedEvent event = buildEvent(spotId.toString(), "A1", "FREE", "OCCUPIED");

        availabilityService.processStateChanged(event);

        var captor = ArgumentCaptor.forClass(SpotStateUpdate.class);
        verify(messagingTemplate).convertAndSend(anyString(), captor.capture());

        SpotStateUpdate update = captor.getValue();
        assertThat(update.getSpotId()).isEqualTo(spotId.toString());
        assertThat(update.getPreviousState()).isEqualTo("FREE");
        assertThat(update.getNewState()).isEqualTo("OCCUPIED");
        assertThat(update.getSpotNumber()).isEqualTo("A1");
    }

    @Test
    void getAvailableSpots_returnsOnlyFreeSpots() {
        UUID spotId2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        AvailabilitySpot occupiedSpot = AvailabilitySpot.builder()
                .id(spotId2).spotNumber("A2").lotId(testSpot.getLotId())
                .floor(1).latitude(6.92710).longitude(79.86130).build();

        when(spotRepository.findWithinRadius(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(testSpot, occupiedSpot));
        when(valueOps.multiGet(anyList()))
                .thenReturn(List.of("FREE", "OCCUPIED"));

        List<AvailableSpotResponse> result =
                availabilityService.getAvailableSpots(6.9272, 79.8615, 500);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSpotNumber()).isEqualTo("A1");
        assertThat(result.get(0).getState()).isEqualTo("FREE");
    }

    @Test
    void getAvailableSpots_noRedisEntry_defaultsToFree() {
        when(spotRepository.findWithinRadius(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(testSpot));
        when(valueOps.multiGet(anyList())).thenReturn(List.of((String) null));

        List<AvailableSpotResponse> result =
                availabilityService.getAvailableSpots(6.9272, 79.8615, 500);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getState()).isEqualTo("FREE");
    }

    @Test
    void processStateChanged_redisFailure_stillBroadcasts() {
        doThrow(new RuntimeException("Redis down"))
                .when(valueOps).set(anyString(), anyString());

        SpotStateChangedEvent event = buildEvent(spotId.toString(), "A1", "FREE", "OCCUPIED");

        assertThatCode(() -> availabilityService.processStateChanged(event))
                .doesNotThrowAnyException();

        // WebSocket broadcast should still fire despite Redis failure
        verify(messagingTemplate).convertAndSend(eq(AvailabilityService.WS_TOPIC), any(SpotStateUpdate.class));
    }

    private SpotStateChangedEvent buildEvent(String spotId, String spotNumber,
                                              String prev, String next) {
        SpotStateChangedPayload payload = new SpotStateChangedPayload();
        payload.setSpotId(spotId);
        payload.setSpotNumber(spotNumber);
        payload.setLotId("00000000-0000-0000-0000-000000000100");
        payload.setPreviousState(prev);
        payload.setNewState(next);
        payload.setLatitude(6.92710);
        payload.setLongitude(79.86100);
        payload.setSource("simulator");

        SpotStateChangedEvent event = new SpotStateChangedEvent();
        event.setEventType("spot.state.changed");
        event.setTimestamp(Instant.now());
        event.setPayload(payload);
        return event;
    }
}
