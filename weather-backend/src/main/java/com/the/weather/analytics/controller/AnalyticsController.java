package com.the.weather.analytics.controller;

import com.the.weather.analytics.dto.AnalyticsRefreshResponseDto;
import com.the.weather.analytics.dto.AnalyticsRefreshStatusDto;
import com.the.weather.analytics.dto.AnalyticsSummaryDto;
import com.the.weather.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Community weather-accuracy analytics")
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Export current community data and refresh the analytics catalogue")
    @ApiResponse(responseCode = "202", description = "Analytics refresh started")
    @ApiResponse(responseCode = "401", description = "Authentication is required")
    @ApiResponse(responseCode = "502", description = "Analytics refresh could not be started")
    @ApiResponse(responseCode = "504", description = "Analytics refresh request timed out")
    public AnalyticsRefreshResponseDto refresh() {
        return analyticsService.refresh();
    }

    @GetMapping("/refresh/status")
    @Operation(summary = "Get the current analytics catalogue refresh status")
    @ApiResponse(responseCode = "200", description = "Analytics refresh status returned")
    @ApiResponse(responseCode = "401", description = "Authentication is required")
    @ApiResponse(responseCode = "502", description = "Analytics refresh status is unavailable")
    @ApiResponse(responseCode = "504", description = "Analytics refresh status request timed out")
    public AnalyticsRefreshStatusDto refreshStatus() {
        return analyticsService.refreshStatus();
    }

    @GetMapping("/summary")
    @Operation(summary = "Get the latest Athena community analytics summary")
    @ApiResponse(responseCode = "200", description = "Latest analytics summary returned")
    @ApiResponse(responseCode = "401", description = "Authentication is required")
    @ApiResponse(responseCode = "404", description = "No analytics data is available yet")
    @ApiResponse(responseCode = "502", description = "Analytics summary is unavailable")
    @ApiResponse(responseCode = "504", description = "Analytics summary request timed out")
    public AnalyticsSummaryDto summary() {
        return analyticsService.summary();
    }
}
