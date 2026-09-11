package com.the.weather.weather.dto;

import java.util.List;

public record DailyWeatherDto(
        double latitude,
        double longitude,
        String timezone,
        List<DailyWeatherEntryDto> days) {
}
