package com.example.enterpriseassistant.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.enterpriseassistant.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.", request, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> invalidJson(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "A valid JSON request body is required.", request, Map.of());
    }

    @ExceptionHandler(InvalidDocumentException.class)
    ResponseEntity<ApiError> invalidDocument(InvalidDocumentException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "The uploaded file exceeds the 10 MB limit.",
                request, Map.of());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiError> missingUpload(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT", "A multipart file field named 'file' is required.",
                request, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(ex.getHeaders())
                .body(new ApiError(Instant.now(), 405, "METHOD_NOT_ALLOWED",
                        "This endpoint does not support that HTTP method. Use POST with JSON for /api/documents or /api/chat, and multipart field 'file' for /api/documents/upload.",
                        request.getRequestURI(), Map.of()));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ApiError> notFound(HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.", request, Map.of());
    }

    @ExceptionHandler(AiServiceException.class)
    ResponseEntity<ApiError> ai(AiServiceException ex, HttpServletRequest request) {
        logFailure("AI request", ex);
        return response(HttpStatus.BAD_GATEWAY, "AI_SERVICE_FAILED", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(DocumentIngestionException.class)
    ResponseEntity<ApiError> ingestion(DocumentIngestionException ex, HttpServletRequest request) {
        logFailure("Document ingestion", ex);
        return response(HttpStatus.BAD_GATEWAY, "DOCUMENT_INGESTION_FAILED", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> database(DataAccessException ex, HttpServletRequest request) {
        logFailure("Persistence", ex);
        return response(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "Storage is temporarily unavailable.", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        logFailure("Unexpected request", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.", request, Map.of());
    }

    private void logFailure(String operation, Exception ex) {
        // Exception messages (including invalid-header errors) can contain credentials.
        // Types and stack locations provide diagnostics without logging messages or values.
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        log.error("{} failed type={} origin={}", operation, cause.getClass().getSimpleName(), origin(cause));
        for (int depth = 0; depth < 5 && cause.getCause() != null && cause.getCause() != cause; depth++) {
            cause = cause.getCause();
            log.error("{} cause type={} origin={}", operation, cause.getClass().getSimpleName(), origin(cause));
        }
    }

    private String origin(Throwable cause) {
        StackTraceElement[] stack = cause.getStackTrace();
        return stack.length == 0 ? "unavailable" : stack[0].toString();
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message,
            HttpServletRequest request, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code,
                message, request.getRequestURI(), fields));
    }
}
