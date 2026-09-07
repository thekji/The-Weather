package com.the.weather.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.the.weather.auth.dto.LoginRequest;
import com.the.weather.auth.dto.LoginResponse;
import com.the.weather.auth.dto.RegisterRequest;
import com.the.weather.auth.dto.UserResponse;
import com.the.weather.auth.exception.DuplicateEmailException;
import com.the.weather.auth.exception.InvalidCredentialsException;
import com.the.weather.security.JwtService;
import com.the.weather.user.model.User;
import com.the.weather.user.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        User user = new User(
                "user_" + UUID.randomUUID(),
                email,
                request.name(),
                passwordEncoder.encode(request.password()),
                Instant.now(clock));
        if (!userRepository.saveIfEmailAvailable(user)) {
            throw new DuplicateEmailException();
        }

        return UserResponse.from(user);
    }

    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        return new LoginResponse(jwtService.generateToken(user.userID()), UserResponse.from(user));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
