package com.smartparking.notification.service;

import com.smartparking.notification.dto.*;
import com.smartparking.notification.model.Notification;
import com.smartparking.notification.provider.NotificationProvider;
import com.smartparking.notification.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final NotificationRepository notificationRepository;
    private final NotificationProvider notificationProvider;

    // ── Event handlers ────────────────────────────────────────────────────────

    @Transactional
    public void handleReservationEvent(ReservationEvent event) {
        ReservationEventPayload p = event.getPayload();
        if (p == null || p.getUserId() == null) return;

        String title;
        String message;
        String type;

        switch (event.getEventType()) {
            case "reservation.created" -> {
                type = "RESERVATION_CREATED";
                title = "Reservation Confirmed";
                message = "Your parking spot is reserved until " + format(p.getReservedUntil());
            }
            case "reservation.active" -> {
                type = "RESERVATION_ACTIVE";
                title = "Check-in Confirmed";
                message = "You have successfully checked in";
            }
            case "reservation.completed" -> {
                type = "RESERVATION_COMPLETED";
                title = "Check-out Confirmed";
                message = "Your parking session has ended. Thank you!";
            }
            case "reservation.expired" -> {
                type = "RESERVATION_EXPIRED";
                title = "Reservation Expired";
                message = "Your reservation has expired and the spot has been released";
            }
            case "reservation.cancelled" -> {
                type = "RESERVATION_CANCELLED";
                title = "Reservation Cancelled";
                message = "Your reservation has been cancelled";
            }
            default -> {
                log.debug("Ignoring reservation event type: {}", event.getEventType());
                return;
            }
        }

        save(p.getUserId(), type, title, message);
    }

    @Transactional
    public void handlePaymentEvent(PaymentEvent event) {
        PaymentEventPayload p = event.getPayload();
        if (p == null || p.getUserId() == null) return;

        UUID userId;
        try {
            userId = UUID.fromString(p.getUserId());
        } catch (IllegalArgumentException e) {
            log.error("Invalid userId in payment event: {}", p.getUserId());
            return;
        }

        String type;
        String title;
        String message;
        String currency = p.getCurrency() != null ? p.getCurrency() : "USD";

        switch (event.getEventType()) {
            case "payment.success" -> {
                type = "PAYMENT_SUCCESS";
                title = "Payment Successful";
                message = "Payment of " + currency + " " + p.getAmount() + " confirmed";
            }
            case "payment.failed" -> {
                type = "PAYMENT_FAILED";
                title = "Payment Failed";
                message = "Your payment could not be processed. Please try again";
            }
            case "payment.refunded" -> {
                type = "PAYMENT_REFUNDED";
                title = "Refund Processed";
                message = "A refund of " + currency + " " + p.getAmount() + " has been issued";
            }
            default -> {
                log.debug("Ignoring payment event type: {}", event.getEventType());
                return;
            }
        }

        save(userId, type, title, message);
    }

    @Transactional
    public void handlePenaltyEvent(PenaltyEvent event) {
        PenaltyEventPayload p = event.getPayload();
        if (p == null || p.getUserId() == null) return;

        if (!"penalty.issued".equals(event.getEventType())) {
            log.debug("Ignoring penalty event type: {}", event.getEventType());
            return;
        }

        String title;
        String message;

        if (p.getTier() == 1) {
            title = "Overstay Warning";
            message = "You have exceeded your reserved window. Please vacate the spot as soon as possible";
        } else {
            title = "Overstay Fine Issued";
            message = "An overstay fine of USD " + p.getAmount() + " has been issued (Tier " + p.getTier() + ")";
        }

        save(p.getUserId(), "PENALTY_ISSUED", title, message);
    }

    // ── REST operations ───────────────────────────────────────────────────────

    public List<NotificationResponse> getMyNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId, UUID userId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found: " + notificationId));
        if (!n.getUserId().equals(userId)) {
            throw new SecurityException("Access denied to notification: " + notificationId);
        }
        if (!n.isRead()) {
            n.setRead(true);
            n.setReadAt(Instant.now());
            notificationRepository.save(n);
        }
        return toResponse(n);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllReadForUser(userId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void save(UUID userId, String type, String title, String message) {
        Notification n = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .channel("IN_APP")
                .read(false)
                .build();
        notificationRepository.save(n);
        notificationProvider.deliver(n);
    }

    private String format(Instant instant) {
        return instant != null ? FMT.format(instant) : "N/A";
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .userId(n.getUserId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .channel(n.getChannel())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .readAt(n.getReadAt())
                .build();
    }
}
