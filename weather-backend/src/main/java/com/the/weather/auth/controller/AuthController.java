package com.the.weather.auth.controller;

import com.the.weather.auth.dto.LoginRequest;
import com.the.weather.auth.dto.LoginResponse;
import com.the.weather.auth.dto.RegisterRequest;
import com.the.weather.auth.dto.UserResponse;
import com.the.weather.auth.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Register and log in")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a user")
    @ApiResponse(responseCode = "201", description = "User registered")
    @ApiResponse(responseCode = "400", description = "Request is invalid")
    @ApiResponse(responseCode = "409", description = "Email already exists")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in")
    @ApiResponse(responseCode = "200", description = "Login succeeded")
    @ApiResponse(responseCode = "400", description = "Request is invalid")
    @ApiResponse(responseCode = "401", description = "Credentials are invalid")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
