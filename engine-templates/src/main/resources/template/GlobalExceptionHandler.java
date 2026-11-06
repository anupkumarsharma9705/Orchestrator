package {{package}};

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
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
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

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

        LOG.warn("Validation failed for {}: {}", sanitize(request.getRequestURI()), sanitize(errors.toString()));
        
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
        
        LOG.warn("Constraint violation for {}: {}", sanitize(request.getRequestURI()), sanitize(errors.toString()));
        
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
        
        LOG.warn("Malformed request body for {}: {}",
            sanitize(request.getRequestURI()), sanitize(ex.getMessage()));
        
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
            ex.getName(), requiredTypeName(ex));
        
        LOG.warn("Type mismatch for {}: {}", sanitize(request.getRequestURI()), sanitize(message));
        
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
        
        LOG.warn("Data integrity violation for {}: {}",
            sanitize(request.getRequestURI()), sanitize(cause));
        
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
        
        LOG.warn("Access denied for {}: {}",
            sanitize(request.getRequestURI()), sanitize(ex.getMessage()));
        
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
        
        LOG.warn("Bad credentials for {}: {}",
            sanitize(request.getRequestURI()), sanitize(ex.getMessage()));
        
        return buildErrorResponse(
            HttpStatus.UNAUTHORIZED,
            "Invalid credentials",
            null,
            request.getRequestURI()
        );
    }

    /**
     * Handles a client that disconnected before the response was written, for example
     * when curl is interrupted from the console. This is not a server error and the
     * response may already be committed, so it is only logged at debug level.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnect(AsyncRequestNotUsableException ex) {
        LOG.debug("Client disconnected before the response completed: {}",
            sanitize(ex.getMessage()));
    }

    /**
     * Handles all other uncaught exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        // Log full stack trace for debugging
        LOG.error("Unhandled exception for {}: {}",
            sanitize(request.getRequestURI()), sanitize(ex.getMessage()), ex);
        
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
     * Returns a null-safe display name for the required parameter type.
     */
    private static String requiredTypeName(MethodArgumentTypeMismatchException ex) {
        Class<?> requiredType = ex.getRequiredType();
        return requiredType == null ? "unknown" : requiredType.getSimpleName();
    }

    /**
     * Removes CR/LF characters so user-controlled values cannot forge log lines.
     */
    private static String sanitize(String value) {
        return value == null ? "" : value.replace('\r', '_').replace('\n', '_');
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
    ) {
        public ErrorResponse {
            details = details == null ? null : Map.copyOf(details);
        }
    }
}