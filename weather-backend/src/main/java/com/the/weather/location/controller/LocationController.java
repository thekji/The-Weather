package com.the.weather.location.controller;

import java.util.List;

import com.the.weather.location.dto.LocationSearchResultDto;
import com.the.weather.location.service.LocationSearchService;

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
@RequestMapping("/api/locations")
@Tag(name = "Locations", description = "Place and address search")
public class LocationController {

    private static final int MAX_QUERY_LENGTH = 200;

    private final LocationSearchService locationSearchService;

    public LocationController(LocationSearchService locationSearchService) {
        this.locationSearchService = locationSearchService;
    }

    @GetMapping("/search")
    @Operation(summary = "Search locations by place name or address")
    @ApiResponse(responseCode = "200", description = "Normalized matching locations")
    @ApiResponse(responseCode = "400", description = "The query is blank or too long")
    public List<LocationSearchResultDto> search(
            @Parameter(description = "Place name, postcode, or free-form address", required = true)
            @RequestParam("q") String query) {
        String normalizedQuery = query.trim();
        if (normalizedQuery.isEmpty() || normalizedQuery.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "q must contain between 1 and 200 characters");
        }

        return locationSearchService.search(normalizedQuery);
    }

    @GetMapping("/reverse")
    @Operation(summary = "Find the nearest normalized location for coordinates")
    @ApiResponse(responseCode = "200", description = "Nearest normalized location")
    @ApiResponse(responseCode = "400", description = "Coordinates are invalid")
    @ApiResponse(responseCode = "404", description = "No nearby location was found")
    public LocationSearchResultDto reverse(
            @Parameter(required = true) @RequestParam double latitude,
            @Parameter(required = true) @RequestParam double longitude) {
        validateCoordinate(latitude, "latitude", -90, 90);
        validateCoordinate(longitude, "longitude", -180, 180);
        return locationSearchService.reverse(latitude, longitude);
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
