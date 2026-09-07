package com.the.weather.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.the.weather.user.model.User;

public record UserResponse(
        @JsonProperty("userID") String userID,
        String email,
        String name) {

    public static UserResponse from(User user) {
        return new UserResponse(user.userID(), user.email(), user.name());
    }
}
