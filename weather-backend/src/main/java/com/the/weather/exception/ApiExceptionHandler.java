package com.the.weather.exception;

import java.time.Clock;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.the.weather.auth.controller.AuthController;
import com.the.weather.auth.exception.DuplicateEmailException;
import com.the.weather.auth.exception.InvalidCredentialsException;

@RestControllerAdvice(assignableTypes = AuthController.class)
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
