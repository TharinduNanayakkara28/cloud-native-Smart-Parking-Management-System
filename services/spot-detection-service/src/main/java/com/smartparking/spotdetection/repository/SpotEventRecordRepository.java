package com.smartparking.spotdetection.repository;

import com.smartparking.spotdetection.model.SpotEventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpotEventRecordRepository extends JpaRepository<SpotEventRecord, UUID> {
    List<SpotEventRecord> findBySpotIdOrderByCreatedAtDesc(UUID spotId);
}
