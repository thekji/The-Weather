package com.the.weather.star.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStarRequest(
        @NotBlank String locationID,
        @NotBlank String name,
        @NotBlank String address,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {

    public CreateStarRequest {
        locationID = trim(locationID);
        name = trim(name);
        address = trim(address);
    }

    @JsonAnySetter
    public void rejectUnknownProperty(String propertyName, Object value) {
        throw new IllegalArgumentException("Unknown star property: " + propertyName);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
