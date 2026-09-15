package com.the.weather.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.the.weather.auth.dto.LoginRequest;
import com.the.weather.auth.dto.LoginResponse;
import com.the.weather.auth.dto.RegisterRequest;
import com.the.weather.auth.dto.UserResponse;
import com.the.weather.auth.exception.DuplicateEmailException;
import com.the.weather.auth.exception.InvalidCredentialsException;
import com.the.weather.security.JwtService;
import com.the.weather.user.model.User;
import com.the.weather.user.repository.UserRepository;

class AuthServiceTests {

    private static final String TEST_SECRET = "test-only-secret-with-more-than-32-bytes";
    private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");

    private final InMemoryUserRepository repository = new InMemoryUserRepository();
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtService jwtService = new JwtService(TEST_SECRET, clock);

    private AuthService service;

    @BeforeEach
    void setUp() {
        repository.users.clear();
        repository.lastLookupEmail = null;
        service = new AuthService(repository, passwordEncoder, jwtService, clock);
    }

    @Test
    void registerStoresACompleteUserWithNormalizedEmailAndBcryptPassword() {
        UserResponse response = service.register(new RegisterRequest(
                " User@Example.COM ",
                " Example User ",
                "password123"));

        User saved = repository.users.get("user@example.com");
        assertThat(saved.userID()).startsWith("user_");
        assertThat(saved.email()).isEqualTo("user@example.com");
        assertThat(saved.name()).isEqualTo("Example User");
        assertThat(saved.createdAt()).isEqualTo(NOW);
        assertThat(saved.passwordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.passwordHash())).isTrue();
        assertThat(response).isEqualTo(new UserResponse(
                saved.userID(),
                "user@example.com",
                "Example User"));
    }

    @Test
    void registerRejectsAnExistingEmailRegardlessOfCaseAndWhitespace() {
        repository.saveIfEmailAvailable(user("user_1", "user@example.com", "stored-password"));

        assertThatThrownBy(() -> service.register(new RegisterRequest(
                " USER@EXAMPLE.COM ",
                "Another User",
                "password123")))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("The email already exists.");

        assertThat(repository.users).hasSize(1);
    }

    @Test
    void loginNormalizesEmailAndReturnsTokenAndSafeUserData() {
        String passwordHash = passwordEncoder.encode("password123");
        repository.saveIfEmailAvailable(user("user_123", "user@example.com", passwordHash));

        LoginResponse response = service.login(new LoginRequest(
                " USER@EXAMPLE.COM ",
                "password123"));

        assertThat(repository.lastLookupEmail).isEqualTo("user@example.com");
        assertThat(response.token()).isNotBlank();
        assertThat(jwtService.getUserID(response.token())).isEqualTo("user_123");
        assertThat(jwtService.getAuthenticatedUser(response.token()).username())
                .isEqualTo("Example User");
        assertThat(response.user()).isEqualTo(new UserResponse(
                "user_123",
                "user@example.com",
                "Example User"));
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnTheSamePublicError() {
        repository.saveIfEmailAvailable(user(
                "user_123",
                "user@example.com",
                passwordEncoder.encode("correct-password")));

        assertThatThrownBy(() -> service.login(new LoginRequest(
                "user@example.com",
                "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Email or password is invalid.");

        assertThatThrownBy(() -> service.login(new LoginRequest(
                "missing@example.com",
                "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Email or password is invalid.");
    }

    private static User user(String userID, String email, String passwordHash) {
        return new User(userID, email, "Example User", passwordHash, NOW);
    }

    private static final class InMemoryUserRepository implements UserRepository {
        private final Map<String, User> users = new HashMap<>();
        private String lastLookupEmail;

        @Override
        public Optional<User> findByEmail(String normalizedEmail) {
            lastLookupEmail = normalizedEmail;
            return Optional.ofNullable(users.get(normalizedEmail));
        }

        @Override
        public boolean saveIfEmailAvailable(User user) {
            return users.putIfAbsent(user.email(), user) == null;
        }
    }
}
