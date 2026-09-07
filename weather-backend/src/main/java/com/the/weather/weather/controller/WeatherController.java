package com.the.weather.weather.controller;

import com.the.weather.weather.dto.CurrentWeatherDto;
import com.the.weather.weather.service.CurrentWeatherService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/weather")
@Tag(name = "Weather", description = "Weather for selected locations")
public class WeatherController {

    private final CurrentWeatherService currentWeatherService;

    public WeatherController(CurrentWeatherService currentWeatherService) {
        this.currentWeatherService = currentWeatherService;
    }

    @GetMapping
    @Operation(summary = "Get current weather for coordinates")
    @ApiResponse(responseCode = "200", description = "Current weather returned")
    @ApiResponse(responseCode = "400", description = "Coordinates are invalid")
    public CurrentWeatherDto currentWeather(
            @Parameter(required = true) @RequestParam double latitude,
            @Parameter(required = true) @RequestParam double longitude) {
        validateCoordinate(latitude, "latitude", -90, 90);
        validateCoordinate(longitude, "longitude", -180, 180);
        return currentWeatherService.current(latitude, longitude);
    }

    private static void validateCoordinate(
            double value,
            String name,
            double minimum,
            double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    name + " must be between " + minimum + " and " + maximum);
        }
    }
}
