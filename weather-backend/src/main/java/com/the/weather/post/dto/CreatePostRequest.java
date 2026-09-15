package com.the.weather.post.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreatePostRequest(
        String description,
        String locationName,
        String address,
        Double latitude,
        Double longitude,
        @NotNull @Min(1) @Max(5) Integer weatherAccuracyRating) {
}
