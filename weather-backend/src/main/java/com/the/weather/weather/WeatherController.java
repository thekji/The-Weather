package com.the.weather.weather;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
@Tag(name = "Deployment", description = "Deployment test endpoints")
public class WeatherController {

    private static final String DEPLOYMENT_MESSAGE = "welcome, you have deployed succesfully";

    @GetMapping
    @Operation(summary = "Return a deployment test message")
    @ApiResponse(responseCode = "200", description = "Deployment test message returned")
    public DeploymentMessage currentWeather() {
        return new DeploymentMessage(DEPLOYMENT_MESSAGE);
    }

    public record DeploymentMessage(String message) {
    }
}
