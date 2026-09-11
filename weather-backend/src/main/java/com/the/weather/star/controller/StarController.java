package com.the.weather.star.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.the.weather.star.dto.CreateStarRequest;
import com.the.weather.star.dto.StarredLocationResponse;
import com.the.weather.star.service.StarService;
import com.the.weather.star.service.StarService.CreateStarResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/stars")
@Tag(name = "Stars", description = "Authenticated user's starred locations")
@SecurityRequirement(name = "bearerAuth")
public class StarController {

    private final StarService starService;

    public StarController(StarService starService) {
        this.starService = starService;
    }

    @GetMapping
    @Operation(summary = "List the authenticated user's starred locations")
    @ApiResponse(responseCode = "200", description = "Starred locations returned")
    @ApiResponse(responseCode = "401", description = "Authentication is required")
    public List<StarredLocationResponse> list(Principal principal) {
        return starService.list(principal.getName());
    }

    @PostMapping
    @Operation(summary = "Star a normalized location")
    @ApiResponse(responseCode = "201", description = "Location starred")
    @ApiResponse(responseCode = "200", description = "Location was already starred")
    @ApiResponse(responseCode = "400", description = "Location is invalid")
    @ApiResponse(responseCode = "401", description = "Authentication is required")
    public ResponseEntity<StarredLocationResponse> create(
            Principal principal,
            @Valid @RequestBody CreateStarRequest request) {
        CreateStarResult result = starService.create(principal.getName(), request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.star());
    }

    @DeleteMapping("/{locationID}")
    @Operation(summary = "Unstar one location owned by the authenticated user")
    @ApiResponse(responseCode = "204", description = "Location is no longer starred")
    @ApiResponse(responseCode = "401", description = "Authentication is required")
    public ResponseEntity<Void> delete(
            Principal principal,
            @PathVariable String locationID) {
        starService.delete(principal.getName(), locationID);
        return ResponseEntity.noContent().build();
    }
}
