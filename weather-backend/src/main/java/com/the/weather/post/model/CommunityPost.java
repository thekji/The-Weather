package com.the.weather.post.model;

import java.time.Instant;
import java.util.List;

public record CommunityPost(
        String postID,
        String userID,
        String username,
        String locationID,
        String locationName,
        String address,
        double latitude,
        double longitude,
        String description,
        Instant createdAt,
        List<String> imageKeys,
        ApiWeather apiWeather,
        int weatherAccuracyRating,
        int helpfulCount,
        int notHelpfulCount,
        String feedType) {
}
