package com.the.weather.post.dto;

import java.time.Instant;
import java.util.List;

import com.the.weather.feedback.model.FeedbackType;
import com.the.weather.post.model.CommunityPost;

public record PostResponse(
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
        ApiWeatherResponse apiWeather,
        List<String> imageUrls,
        int weatherAccuracyRating,
        int helpfulCount,
        int notHelpfulCount,
        FeedbackType myFeedback) {

    public static PostResponse from(
            CommunityPost post,
            List<String> imageUrls,
            FeedbackType myFeedback) {
        return new PostResponse(post.postID(), post.userID(), post.username(), post.locationID(),
                post.locationName(), post.address(), post.latitude(), post.longitude(),
                post.description(), post.createdAt(), ApiWeatherResponse.from(post.apiWeather()),
                imageUrls, post.weatherAccuracyRating(), post.helpfulCount(), post.notHelpfulCount(),
                myFeedback);
    }
}
