package com.the.weather.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.the.weather.auth.dto.LoginRequest;
import com.the.weather.auth.dto.LoginResponse;
import com.the.weather.auth.dto.RegisterRequest;
import com.the.weather.auth.dto.UserResponse;
import com.the.weather.auth.exception.DuplicateEmailException;
import com.the.weather.auth.exception.InvalidCredentialsException;
import com.the.weather.auth.service.AuthService;
import com.the.weather.exception.ApiExceptionHandler;

class AuthControllerTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-07T00:00:00Z"),
            ZoneOffset.UTC);

    private final StubAuthService authService = new StubAuthService();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService.registerFailure = null;
        authService.loginFailure = null;
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new ApiExceptionHandler(CLOCK))
                .build();
    }

    @Test
    void registerReturnsCreatedAndNeverReturnsPasswordData() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": " User@Example.COM ",
                                  "name": "Example User",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userID").value("user_123"))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.name").value("Example User"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerRejectsInvalidAndMissingFields() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "name": " ",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid request"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateEmailReturnsControlledConflict() throws Exception {
        authService.registerFailure = new DuplicateEmailException();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "name": "Example User",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("The email already exists."));
    }

    @Test
    void loginReturnsJwtAndSafeUserData() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed.jwt.token"))
                .andExpect(jsonPath("$.user.userID").value("user_123"))
                .andExpect(jsonPath("$.user.email").value("user@example.com"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void invalidLoginReturnsControlledUnauthorizedResponse() throws Exception {
        authService.loginFailure = new InvalidCredentialsException();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Email or password is invalid."));
    }

    @Test
    void unexpectedAuthFailureReturnsControlledInternalError() throws Exception {
        authService.loginFailure = new IllegalStateException("internal database detail");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
    }

    private static final class StubAuthService extends AuthService {
        private RuntimeException registerFailure;
        private RuntimeException loginFailure;

        private StubAuthService() {
            super(null, null, null, CLOCK);
        }

        @Override
        public UserResponse register(RegisterRequest request) {
            if (registerFailure != null) {
                throw registerFailure;
            }
            return new UserResponse("user_123", "user@example.com", "Example User");
        }

        @Override
        public LoginResponse login(LoginRequest request) {
            if (loginFailure != null) {
                throw loginFailure;
            }
            return new LoginResponse(
                    "signed.jwt.token",
                    new UserResponse("user_123", "user@example.com", "Example User"));
        }
    }
}
