package com.the.weather.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final int MINIMUM_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final Duration expiration;
    private final Clock clock;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-minutes}") long expirationMinutes,
            Clock clock) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        }
        if (expirationMinutes <= 0) {
            throw new IllegalStateException("JWT_EXPIRATION_MINUTES must be greater than zero");
        }

        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.expiration = Duration.ofMinutes(expirationMinutes);
        this.clock = clock;
    }

    public String generateToken(String userID) {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .subject(userID)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    public String getUserID(String token) throws JwtException {
        String userID = Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();

        if (!StringUtils.hasText(userID)) {
            throw new MalformedJwtException("JWT subject is missing");
        }
        return userID;
    }
}
