package com.the.weather.star.exception;

public class InvalidStarRequestException extends RuntimeException {

    public InvalidStarRequestException() {
        super("Invalid star request");
    }
}
