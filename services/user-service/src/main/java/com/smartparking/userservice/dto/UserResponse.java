package com.smartparking.userservice.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private Instant createdAt;
}
