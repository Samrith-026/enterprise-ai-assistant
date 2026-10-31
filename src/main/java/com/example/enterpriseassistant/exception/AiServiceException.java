package com.example.enterpriseassistant.exception;

public class AiServiceException extends RuntimeException {
    public AiServiceException(Throwable cause) {
        super("The AI service could not complete the request.", cause);
    }
}
