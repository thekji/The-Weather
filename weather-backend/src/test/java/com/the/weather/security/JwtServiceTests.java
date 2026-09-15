package com.the.weather.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtServiceTests {

    private static final String TEST_SECRET = "test-only-secret-with-more-than-32-bytes";
    private static final Instant LOGIN_TIME = Instant.parse("2026-09-13T00:00:00Z");

    @Test
    void newlyIssuedTokenExpiresExactlyThirtyMinutesAfterLogin() {
        JwtService jwtService = new JwtService(
                TEST_SECRET,
                Clock.fixed(LOGIN_TIME, ZoneOffset.UTC));

        String token = jwtService.generateToken("user_123", "Example User");
        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .clock(() -> Date.from(LOGIN_TIME))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(LOGIN_TIME);
        assertThat(claims.getExpiration().toInstant())
                .isEqualTo(LOGIN_TIME.plus(Duration.ofMinutes(30)));
        assertThat(claims.get("username", String.class)).isEqualTo("Example User");
    }
}
