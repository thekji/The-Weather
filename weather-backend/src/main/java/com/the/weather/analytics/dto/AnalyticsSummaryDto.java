package com.the.weather.analytics.dto;

import java.util.List;

public record AnalyticsSummaryDto(
        long totalPosts,
        Double averageWeatherAccuracy,
        List<RatingDistributionDto> ratingDistribution,
        List<LocationPostCountDto> topLocations,
        String generatedAt) {
}
