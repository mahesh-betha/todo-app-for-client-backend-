package com.todoapp.exception;

import org.springframework.http.HttpStatus;

/**
 * Generic API error carrying an HTTP status; rendered by the
 * {@link GlobalExceptionHandler} into a consistent ErrorResponse body.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

