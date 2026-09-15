package com.the.weather.star.dto;

import java.time.Instant;

import com.the.weather.star.model.StarredLocation;

public record StarredLocationResponse(
        String locationID,
        String name,
        String address,
        double latitude,
        double longitude,
        Instant starredAt) {

    public static StarredLocationResponse from(StarredLocation location) {
        return new StarredLocationResponse(
                location.locationID(),
                location.name(),
                location.address(),
                location.latitude(),
                location.longitude(),
                location.starredAt());
    }
}
