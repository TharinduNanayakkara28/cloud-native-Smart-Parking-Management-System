package com.smartparking.spotdetection.repository;

import com.smartparking.spotdetection.model.Spot;
import com.smartparking.spotdetection.model.SpotState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpotRepository extends JpaRepository<Spot, UUID> {
    List<Spot> findByState(SpotState state);
    List<Spot> findByLotId(UUID lotId);
}
