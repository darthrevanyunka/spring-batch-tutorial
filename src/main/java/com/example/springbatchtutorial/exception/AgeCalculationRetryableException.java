package com.example.springbatchtutorial.exception;

public class AgeCalculationRetryableException extends RuntimeException {
    public AgeCalculationRetryableException(String message) {
        super(message);
    }
}


