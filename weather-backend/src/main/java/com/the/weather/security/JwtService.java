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
    private static final Duration SESSION_LIFETIME = Duration.ofMinutes(30);

    private final SecretKey signingKey;
    private final Clock clock;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            Clock clock) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        }

        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.clock = clock;
    }

    public String generateToken(String userID, String username) {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .subject(userID)
                .claim("username", username)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(SESSION_LIFETIME)))
                .signWith(signingKey)
                .compact();
    }

    public String generateToken(String userID) {
        return generateToken(userID, userID);
    }

    public AuthenticatedUser getAuthenticatedUser(String token) throws JwtException {
        var claims = Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String userID = claims.getSubject();

        if (!StringUtils.hasText(userID)) {
            throw new MalformedJwtException("JWT subject is missing");
        }

        String username = claims.get("username", String.class);
        return new AuthenticatedUser(userID,
                StringUtils.hasText(username) ? username : userID);
    }

    public String getUserID(String token) throws JwtException {
        return getAuthenticatedUser(token).userID();
    }
}
