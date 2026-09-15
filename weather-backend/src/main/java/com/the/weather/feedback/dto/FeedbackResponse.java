package com.the.weather.feedback.dto;

import com.the.weather.feedback.model.FeedbackType;

public record FeedbackResponse(
        String postID,
        FeedbackType myFeedback,
        int helpfulCount,
        int notHelpfulCount) {
}
