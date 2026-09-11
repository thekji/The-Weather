package com.the.weather.weather.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Hourly weather forecast for a selected location")
public record HourlyWeatherDto(
        double latitude,
        double longitude,
        String timezone,
        List<HourlyWeatherEntryDto> hours) {
}
