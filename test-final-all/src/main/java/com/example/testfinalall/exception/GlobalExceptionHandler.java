package com.example.testfinalall.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for consistent error responses.
 * 
 * Security Rules:
 * - SEC-7: No secrets in code/logs - sanitize error messages
 * - SEC-6: @Valid on @RequestBody - validation errors handled
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles validation errors from @Valid annotations.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        log.warn("Validation failed for {}: {}", request.getRequestURI(), errors);
        
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Validation failed",
            errors,
            request.getRequestURI()
        );
    }

    /**
     * Handles constraint violations (e.g., @Valid on path variables).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(v -> 
            errors.put(v.getPropertyPath().toString(), v.getMessage()));
        
        log.warn("Constraint violation for {}: {}", request.getRequestURI(), errors);
        
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Constraint violation",
            errors,
            request.getRequestURI()
        );
    }

    /**
     * Handles invalid request body (malformed JSON).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        
        log.warn("Malformed request body for {}: {}", request.getRequestURI(), ex.getMessage());
        
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Malformed request body",
            null,
            request.getRequestURI()
        );
    }

    /**
     * Handles type mismatch in path variables or query parameters.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        
        String message = String.format("Parameter '%s' should be of type %s",
            ex.getName(), ex.getRequiredType().getSimpleName());
        
        log.warn("Type mismatch for {}: {}", request.getRequestURI(), message);
        
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            message,
            null,
            request.getRequestURI()
        );
    }

    /**
     * Handles database constraint violations.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        
        String message = "Data integrity violation";
        String cause = ex.getMostSpecificCause().getMessage();
        
        // Sanitize - don't expose database details
        if (cause != null && cause.contains("duplicate key")) {
            message = "A record with this value already exists";
        } else if (cause != null && cause.contains("foreign key")) {
            message = "Referenced record does not exist";
        }
        
        log.warn("Data integrity violation for {}: {}", request.getRequestURI(), cause);
        
        return buildErrorResponse(
            HttpStatus.CONFLICT,
            message,
            null,
            request.getRequestURI()
        );
    }

    /**
     * Handles access denied (authorization failures).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        
        log.warn("Access denied for {}: {}", request.getRequestURI(), ex.getMessage());
        
        return buildErrorResponse(
            HttpStatus.FORBIDDEN,
            "Access denied: insufficient permissions",
            null,
            request.getRequestURI()
        );
    }

    /**
     * Handles bad credentials (authentication failures).
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        
        log.warn("Bad credentials for {}: {}", request.getRequestURI(), ex.getMessage());
        
        return buildErrorResponse(
            HttpStatus.UNAUTHORIZED,
            "Invalid credentials",
            null,
            request.getRequestURI()
        );
    }

    /**
     * Handles all other uncaught exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        // Log full stack trace for debugging
        log.error("Unhandled exception for {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        
        // Sanitize - don't expose internal details
        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An internal server error occurred",
            null,
            request.getRequestURI()
        );
    }

    /**
     * Builds standardized error response.
     */
    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status, String message, Map<String, String> details, String path) {
        
        ErrorResponse error = new ErrorResponse(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            path,
            details
        );
        
        return ResponseEntity.status(status).body(error);
    }

    /**
     * Standard error response format.
     */
    public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> details
    ) {}
}