package com.the.weather.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank String name,
        @NotBlank String password) {

    public RegisterRequest {
        email = email == null ? null : email.trim();
        name = name == null ? null : name.trim();
    }

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", name=" + name + ", password=[REDACTED]]";
    }
}
