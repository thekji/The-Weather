package com.the.weather.auth.dto;

public record LoginResponse(String token, UserResponse user) {

    @Override
    public String toString() {
        return "LoginResponse[token=[REDACTED], user=" + user + "]";
    }
}
