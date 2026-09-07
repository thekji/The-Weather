package com.the.weather.user.model;

import java.time.Instant;

public record User(
        String userID,
        String email,
        String name,
        String passwordHash,
        Instant createdAt) {

    @Override
    public String toString() {
        return "User[userID=" + userID
                + ", email=" + email
                + ", name=" + name
                + ", passwordHash=[REDACTED]"
                + ", createdAt=" + createdAt + "]";
    }
}
