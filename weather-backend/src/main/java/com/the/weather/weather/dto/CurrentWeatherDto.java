package com.the.weather.weather.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Normalized current weather for a selected location")
public record CurrentWeatherDto(
    double latitude,
    double longitude,
    String recordedAt,
    String timezone,
    double temperatureCelsius,
    double apparentTemperatureCelsius,
    double relativeHumidityPercent,
    double precipitationMillimetres,
    double rainMillimetres,
    double cloudCoverPercent,
    double windSpeedKilometresPerHour,
    double windDirectionDegrees,
    int weatherCode,
    String condition,
    boolean daytime) {
}
