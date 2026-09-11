package com.the.weather.weather.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Normalized weather values for one forecast hour")
public record HourlyWeatherEntryDto(
        String time,
        double temperatureCelsius,
        double precipitationProbabilityPercent,
        int weatherCode,
        String condition,
        double uvIndex,
        double uvIndexClearSky,
        boolean daytime) {
}
