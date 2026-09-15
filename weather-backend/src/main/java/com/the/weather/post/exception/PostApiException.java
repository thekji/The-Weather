package com.the.weather.post.exception;

import org.springframework.http.HttpStatus;

public class PostApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public PostApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
