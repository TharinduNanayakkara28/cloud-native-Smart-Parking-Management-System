package com.smartparking.reservation.service;

import com.smartparking.reservation.dto.SpotStateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Component
@Slf4j
public class AvailabilityClient {

    private final RestTemplate restTemplate;
    private final String availabilityServiceUrl;

    public AvailabilityClient(
            RestTemplate restTemplate,
            @Value("${availability.service.url:http://localhost:8084}") String availabilityServiceUrl) {
        this.restTemplate = restTemplate;
        this.availabilityServiceUrl = availabilityServiceUrl;
    }

    public String getSpotState(UUID spotId) {
        try {
            SpotStateResponse response = restTemplate.getForObject(
                    availabilityServiceUrl + "/spots/" + spotId + "/state",
                    SpotStateResponse.class);
            return response != null ? response.getState() : "FREE";
        } catch (RestClientException e) {
            // If availability service is unreachable, allow the reservation and let the lock arbitrate.
            log.warn("Availability service unreachable for spot {}, proceeding optimistically: {}", spotId, e.getMessage());
            return "FREE";
        }
    }
}
