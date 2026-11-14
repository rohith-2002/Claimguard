package com.claimguard.api;

import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/** Maps every client error to 400 {error, fieldErrors}. Never echoes request values back. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalidBody(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return badRequest(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> invalidParams(HandlerMethodValidationException ex) {
        Map<String, String> errors = new TreeMap<>();
        ex.getAllValidationResults().forEach(result ->
                result.getResolvableErrors().forEach(e ->
                        errors.putIfAbsent(String.valueOf(result.getMethodParameter().getParameterName()),
                                e.getDefaultMessage())));
        return badRequest(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> constraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new TreeMap<>();
        ex.getConstraintViolations()
                .forEach(v -> errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return badRequest(errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest(Map.of(ex.getName(), "invalid value"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException ex) {
        return badRequest(Map.of("body", "malformed JSON or invalid value"));
    }

    private static ResponseEntity<Map<String, Object>> badRequest(Map<String, String> fieldErrors) {
        return ResponseEntity.badRequest().body(Map.of("error", "Validation failed", "fieldErrors", fieldErrors));
    }
}
