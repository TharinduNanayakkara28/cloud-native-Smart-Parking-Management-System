package com.smartparking.notification.repository;

import com.smartparking.notification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndReadFalse(UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.read = false")
    void markAllReadForUser(UUID userId);
}
