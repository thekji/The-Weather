package com.the.weather.feedback.model;

import java.time.Instant;

public record PostFeedback(
        String postID,
        String userID,
        FeedbackType feedbackType,
        Instant createdAt) {
}
