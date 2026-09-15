package com.the.weather.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.the.weather.post.model.ApiWeather;

public record ApiWeatherResponse(
        String recordedAt,
        @JsonProperty("weather_code") int weatherCode,
        String condition) {

    public static ApiWeatherResponse from(ApiWeather weather) {
        return new ApiWeatherResponse(weather.recordedAt(), weather.weatherCode(), weather.condition());
    }
}
