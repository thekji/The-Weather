package com.the.weather.exception;

import java.time.Clock;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.the.weather.auth.exception.DuplicateEmailException;
import com.the.weather.auth.exception.InvalidCredentialsException;
import com.the.weather.star.exception.InvalidStarRequestException;
import com.the.weather.post.exception.PostApiException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    ResponseEntity<ApiError> invalidRequest() {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> invalidCredentials(InvalidCredentialsException exception) {
        return response(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", exception.getMessage());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ApiError> duplicateEmail(DuplicateEmailException exception) {
        return response(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", exception.getMessage());
    }

    @ExceptionHandler(InvalidStarRequestException.class)
    ResponseEntity<ApiError> invalidStarRequest() {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request");
    }

    @ExceptionHandler(PostApiException.class)
    ResponseEntity<ApiError> postError(PostApiException exception) {
        return response(exception.status(), exception.code(), exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge() {
        return response(HttpStatus.BAD_REQUEST, "IMAGE_TOO_LARGE",
                "Each community image must be 5 MB or smaller.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> responseStatus(ResponseStatusException exception) {
        String message = exception.getReason() == null ? "Request failed" : exception.getReason();
        return response(HttpStatus.valueOf(exception.getStatusCode().value()),
                "REQUEST_FAILED", message);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpectedError() {
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred.");
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(
                status.value(),
                code,
                message,
                Instant.now(clock)));
    }
}
