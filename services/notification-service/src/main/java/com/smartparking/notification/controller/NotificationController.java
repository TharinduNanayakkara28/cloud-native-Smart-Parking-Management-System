package com.smartparking.notification.controller;

import com.smartparking.notification.dto.NotificationResponse;
import com.smartparking.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/user/me")
    public ResponseEntity<List<NotificationResponse>> getMyNotifications(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(notificationService.getMyNotifications(userId));
    }

    @GetMapping("/user/me/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(Map.of("unreadCount", notificationService.getUnreadCount(userId)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.markRead(id, userId));
    }

    @PostMapping("/user/me/read-all")
    public ResponseEntity<Void> markAllRead(@RequestHeader("X-User-Id") UUID userId) {
        notificationService.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }
}
