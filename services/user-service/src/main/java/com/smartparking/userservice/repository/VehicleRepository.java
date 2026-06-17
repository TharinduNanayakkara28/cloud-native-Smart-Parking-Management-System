package com.smartparking.userservice.repository;

import com.smartparking.userservice.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    List<Vehicle> findByUserId(UUID userId);
    boolean existsByPlate(String plate);
}
