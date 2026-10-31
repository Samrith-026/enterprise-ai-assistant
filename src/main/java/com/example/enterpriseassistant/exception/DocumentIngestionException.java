package com.example.enterpriseassistant.exception;

public class DocumentIngestionException extends RuntimeException {
    public DocumentIngestionException(Throwable cause) {
        super("Document ingestion could not be completed.", cause);
    }
}
