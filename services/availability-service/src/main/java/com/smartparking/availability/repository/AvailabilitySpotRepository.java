package com.smartparking.availability.repository;

import com.smartparking.availability.model.AvailabilitySpot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AvailabilitySpotRepository extends JpaRepository<AvailabilitySpot, UUID> {

    /**
     * Find spots within radiusMeters of (lat, lng) using PostGIS ST_DWithin.
     * Selects only non-geometry columns so JPA mapping works without Hibernate Spatial.
     * Note: ST_MakePoint takes (longitude, latitude) — x then y.
     */
    @Query(value = """
            SELECT id, lot_id, spot_number, floor, latitude, longitude
            FROM availability_spots
            WHERE ST_DWithin(
                location,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                :radiusMeters
            )
            """, nativeQuery = true)
    List<AvailabilitySpot> findWithinRadius(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusMeters") double radiusMeters);
}
