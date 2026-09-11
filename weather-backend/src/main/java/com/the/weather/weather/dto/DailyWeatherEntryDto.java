package com.the.weather.weather.dto;

public record DailyWeatherEntryDto(
        String date,
        double maximumTemperatureCelsius,
        double minimumTemperatureCelsius,
        String sunrise,
        String sunset,
        double precipitationProbabilityMaxPercent,
        int weatherCode,
        String condition) {
}
