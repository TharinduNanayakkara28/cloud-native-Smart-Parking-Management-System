package com.smartparking.userservice.service;

import com.smartparking.userservice.dto.*;
import com.smartparking.userservice.model.RefreshToken;
import com.smartparking.userservice.model.User;
import com.smartparking.userservice.repository.RefreshTokenRepository;
import com.smartparking.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .build();
        userRepository.save(user);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(rawRefreshToken)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(stored);
            throw new BadCredentialsException("Refresh token expired");
        }
        User user = stored.getUser();
        refreshTokenRepository.delete(stored);
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String rawRefresh = jwtService.generateRefreshToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(rawRefresh)
                .expiresAt(jwtService.refreshTokenExpiry())
                .build();
        refreshTokenRepository.save(refreshToken);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefresh)
                .build();
    }
}
