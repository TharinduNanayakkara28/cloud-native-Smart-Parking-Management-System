package com.smartparking.userservice.service;

import com.smartparking.userservice.dto.UserResponse;
import com.smartparking.userservice.dto.VehicleRequest;
import com.smartparking.userservice.dto.VehicleResponse;
import com.smartparking.userservice.model.Vehicle;
import com.smartparking.userservice.repository.UserRepository;
import com.smartparking.userservice.repository.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;

    public UserResponse getUser(UUID userId) {
        return userRepository.findById(userId)
                .map(u -> UserResponse.builder()
                        .id(u.getId())
                        .name(u.getName())
                        .email(u.getEmail())
                        .phone(u.getPhone())
                        .createdAt(u.getCreatedAt())
                        .build())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    @Transactional
    public VehicleResponse addVehicle(UUID userId, VehicleRequest request) {
        if (vehicleRepository.existsByPlate(request.getPlate())) {
            throw new IllegalArgumentException("Plate already registered");
        }
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        Vehicle vehicle = Vehicle.builder()
                .user(user)
                .plate(request.getPlate())
                .make(request.getMake())
                .model(request.getModel())
                .build();
        vehicleRepository.save(vehicle);
        return toVehicleResponse(vehicle);
    }

    public List<VehicleResponse> getVehicles(UUID userId) {
        return vehicleRepository.findByUserId(userId).stream()
                .map(this::toVehicleResponse)
                .collect(Collectors.toList());
    }

    private VehicleResponse toVehicleResponse(Vehicle v) {
        return VehicleResponse.builder()
                .id(v.getId())
                .plate(v.getPlate())
                .make(v.getMake())
                .model(v.getModel())
                .build();
    }
}
