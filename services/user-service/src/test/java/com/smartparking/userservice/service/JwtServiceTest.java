package com.smartparking.userservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "test-secret-key-that-is-long-enough-32chars!",
                900_000L,
                604_800_000L
        );
    }

    @Test
    void generateAccessToken_containsSubjectAndEmail() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, "test@example.com");
        var claims = jwtService.parseAccessToken(token);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo("test@example.com");
    }

    @Test
    void isAccessTokenValid_validToken_returnsTrue() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), "a@b.com");
        assertThat(jwtService.isAccessTokenValid(token)).isTrue();
    }

    @Test
    void isAccessTokenValid_tamperedToken_returnsFalse() {
        assertThat(jwtService.isAccessTokenValid("not.a.valid.token")).isFalse();
    }

    @Test
    void isAccessTokenValid_emptyString_returnsFalse() {
        assertThat(jwtService.isAccessTokenValid("")).isFalse();
    }

    @Test
    void generateRefreshToken_isUuidFormat() {
        String refresh = jwtService.generateRefreshToken();
        assertThatCode(() -> UUID.fromString(refresh)).doesNotThrowAnyException();
    }

    @Test
    void refreshTokenExpiry_isInFuture() {
        assertThat(jwtService.refreshTokenExpiry()).isAfter(java.time.Instant.now());
    }
}
