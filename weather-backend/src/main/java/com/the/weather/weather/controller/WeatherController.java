package com.the.weather.weather.controller;

import com.the.weather.weather.dto.CurrentWeatherDto;
import com.the.weather.weather.dto.DailyWeatherDto;
import com.the.weather.weather.dto.HourlyWeatherDto;
import com.the.weather.weather.service.CurrentWeatherService;
import com.the.weather.weather.service.DailyWeatherService;
import com.the.weather.weather.service.HourlyWeatherService;

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
    private final HourlyWeatherService hourlyWeatherService;
    private final DailyWeatherService dailyWeatherService;

    public WeatherController(
            CurrentWeatherService currentWeatherService,
            HourlyWeatherService hourlyWeatherService,
            DailyWeatherService dailyWeatherService) {
        this.currentWeatherService = currentWeatherService;
        this.hourlyWeatherService = hourlyWeatherService;
        this.dailyWeatherService = dailyWeatherService;
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

    @GetMapping("/hourly")
    @Operation(summary = "Get an hourly weather forecast for coordinates")
    @ApiResponse(responseCode = "200", description = "Hourly weather returned")
    @ApiResponse(responseCode = "400", description = "Coordinates are invalid")
    public HourlyWeatherDto hourlyWeather(
            @Parameter(required = true) @RequestParam double latitude,
            @Parameter(required = true) @RequestParam double longitude) {
        validateCoordinate(latitude, "latitude", -90, 90);
        validateCoordinate(longitude, "longitude", -180, 180);
        return hourlyWeatherService.hourly(latitude, longitude);
    }

    @GetMapping("/daily")
    @Operation(summary = "Get a seven-day weather forecast for coordinates")
    @ApiResponse(responseCode = "200", description = "Daily weather returned")
    @ApiResponse(responseCode = "400", description = "Coordinates are invalid")
    public DailyWeatherDto dailyWeather(
            @Parameter(required = true) @RequestParam double latitude,
            @Parameter(required = true) @RequestParam double longitude) {
        validateCoordinate(latitude, "latitude", -90, 90);
        validateCoordinate(longitude, "longitude", -180, 180);
        return dailyWeatherService.daily(latitude, longitude);
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
