package com.sujay.distributed_rate_limiter.exceptionHandling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice   // applies to all @RestController classes
public class GlobalExceptionHandler {

    // Handles @Valid failures — e.g. missing clientKey in the request body
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        // Collect all field-level validation messages into a list
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "status",    400,
                        "error",     "Validation failed",
                        "message",   errors,
                        "timestamp", Instant.now().toString()
                ));
    }

    // Handles unknown algorithm, missing implementation, etc.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "status",    400,
                        "error",     "Bad request",
                        "message",   ex.getMessage(),
                        "timestamp", Instant.now().toString()
                ));
    }

    // Catch-all — something unexpected happened
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericError(Exception ex) {
        log.error("[GlobalExceptionHandler] Unhandled exception", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "status",    500,
                        "error",     "Internal server error",
                        "message",   "Something went wrong. Check server logs.",
                        "timestamp", Instant.now().toString()
                ));
    }
}
