package com.the.weather.exception;

import java.time.Instant;

public record ApiError(int status, String code, String message, Instant timestamp) {
}
