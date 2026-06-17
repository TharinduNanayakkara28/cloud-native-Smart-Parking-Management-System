package com.smartparking.userservice.controller;

import com.smartparking.userservice.dto.UserResponse;
import com.smartparking.userservice.dto.VehicleRequest;
import com.smartparking.userservice.dto.VehicleResponse;
import com.smartparking.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(userService.getUser(userId));
    }

    @PostMapping("/me/vehicles")
    public ResponseEntity<VehicleResponse> addVehicle(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.addVehicle(userId, request));
    }

    @GetMapping("/me/vehicles")
    public ResponseEntity<List<VehicleResponse>> getVehicles(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(userService.getVehicles(userId));
    }
}
