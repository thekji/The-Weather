package com.the.weather.auth.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("The email already exists.");
    }
}
