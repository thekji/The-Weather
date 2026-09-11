package com.the.weather.star.model;

import java.time.Instant;

public record StarredLocation(
        String userID,
        String locationID,
        String name,
        String address,
        double latitude,
        double longitude,
        Instant starredAt,
        boolean weatherAlertsEnabled) {
}
