package com.smartparking.spotdetection.service;

import com.smartparking.spotdetection.dto.SpotSensorEvent;
import com.smartparking.spotdetection.dto.SpotSensorPayload;
import com.smartparking.spotdetection.kafka.SpotStatePublisher;
import com.smartparking.spotdetection.model.Spot;
import com.smartparking.spotdetection.model.SpotState;
import com.smartparking.spotdetection.repository.SpotEventRecordRepository;
import com.smartparking.spotdetection.repository.SpotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpotDetectionServiceTest {

    @Mock SpotRepository spotRepository;
    @Mock SpotEventRecordRepository eventRecordRepository;
    @Mock SpotStatePublisher publisher;

    @InjectMocks SpotDetectionService service;

    private Spot testSpot;
    private UUID spotId;

    @BeforeEach
    void setUp() {
        spotId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        testSpot = Spot.builder()
                .id(spotId)
                .lotId(UUID.fromString("00000000-0000-0000-0000-000000000100"))
                .spotNumber("A1")
                .floor(1)
                .state(SpotState.FREE)
                .latitude(6.92710)
                .longitude(79.86100)
                .build();
    }

    @Test
    void processEvent_freeToOccupied_updatesStateAndPublishes() {
        when(spotRepository.findById(spotId)).thenReturn(Optional.of(testSpot));
        when(spotRepository.save(any())).thenReturn(testSpot);

        SpotSensorEvent event = buildEvent(spotId.toString(), "A1", "OCCUPIED");
        service.processEvent(event);

        assertThat(testSpot.getState()).isEqualTo(SpotState.OCCUPIED);
        verify(eventRecordRepository).save(any());
        verify(publisher).publish(any());
    }

    @Test
    void processEvent_publishesCorrectPreviousAndNewState() {
        when(spotRepository.findById(spotId)).thenReturn(Optional.of(testSpot));
        when(spotRepository.save(any())).thenReturn(testSpot);

        service.processEvent(buildEvent(spotId.toString(), "A1", "OCCUPIED"));

        var captor = ArgumentCaptor.forClass(
                com.smartparking.spotdetection.dto.SpotStateChangedEvent.class);
        verify(publisher).publish(captor.capture());

        var payload = captor.getValue().getPayload();
        assertThat(payload.getPreviousState()).isEqualTo("FREE");
        assertThat(payload.getNewState()).isEqualTo("OCCUPIED");
        assertThat(payload.getSpotNumber()).isEqualTo("A1");
        assertThat(captor.getValue().getEventType()).isEqualTo("spot.state.changed");
    }

    @Test
    void processEvent_unknownSpot_skipsWithoutException() {
        when(spotRepository.findById(any())).thenReturn(Optional.empty());

        assertThatCode(() -> service.processEvent(
                buildEvent(UUID.randomUUID().toString(), "X1", "OCCUPIED")))
                .doesNotThrowAnyException();

        verifyNoInteractions(publisher, eventRecordRepository);
    }

    private SpotSensorEvent buildEvent(String spotId, String spotNumber, String state) {
        SpotSensorPayload payload = new SpotSensorPayload();
        payload.setSpotId(spotId);
        payload.setSpotNumber(spotNumber);
        payload.setLotId("00000000-0000-0000-0000-000000000100");
        payload.setState(state);
        payload.setSource("simulator");

        SpotSensorEvent event = new SpotSensorEvent();
        event.setEventType(state.equals("OCCUPIED") ? "spot.occupied" : "spot.freed");
        event.setTimestamp(java.time.Instant.now());
        event.setPayload(payload);
        return event;
    }
}
