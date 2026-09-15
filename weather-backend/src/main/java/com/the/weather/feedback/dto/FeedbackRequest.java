package com.the.weather.feedback.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.the.weather.feedback.model.FeedbackType;

import jakarta.validation.constraints.NotNull;

public record FeedbackRequest(@NotNull FeedbackType feedbackType) {

    @JsonAnySetter
    public void rejectUnknownProperty(String propertyName, Object value) {
        throw new IllegalArgumentException("Unknown feedback property: " + propertyName);
    }
}
