package com.smartparking.userservice.service;

import com.smartparking.userservice.dto.LoginRequest;
import com.smartparking.userservice.dto.RegisterRequest;
import com.smartparking.userservice.model.User;
import com.smartparking.userservice.repository.RefreshTokenRepository;
import com.smartparking.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;

    @InjectMocks AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .name("Alice")
                .email("alice@example.com")
                .password("encoded-password")
                .build();
    }

    @Test
    void register_newEmail_returnsAccessAndRefreshTokens() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any())).thenReturn(testUser);
        when(jwtService.generateAccessToken(any(), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-uuid");
        when(jwtService.refreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(3600));

        var request = new RegisterRequest();
        request.setName("Alice");
        request.setEmail("alice@example.com");
        request.setPassword("password123");

        var response = authService.register(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-uuid");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    void register_duplicateEmail_throwsIllegalArgument() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        var request = new RegisterRequest();
        request.setName("Alice");
        request.setEmail("alice@example.com");
        request.setPassword("password123");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email already registered");
    }

    @Test
    void login_validCredentials_returnsTokens() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtService.generateAccessToken(any(), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-uuid");
        when(jwtService.refreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(3600));

        var request = new LoginRequest();
        request.setEmail("alice@example.com");
        request.setPassword("password123");

        var response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        var request = new LoginRequest();
        request.setEmail("alice@example.com");
        request.setPassword("wrong");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void login_unknownEmail_throwsBadCredentials() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        var request = new LoginRequest();
        request.setEmail("nobody@example.com");
        request.setPassword("password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }
}
