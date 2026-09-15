package com.the.weather.analytics.dto;

public record AnalyticsRefreshResponseDto(
        AnalyticsRefreshStatus status,
        String message) {
}
