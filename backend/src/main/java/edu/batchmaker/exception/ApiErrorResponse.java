package edu.batchmaker.exception;

import java.time.Instant;
import java.util.Map;

/** Uniform error envelope for every failed request (spec section 42). */
public record ApiErrorResponse(
        boolean success,
        String errorCode,
        String message,
        String path,
        Map<String, Object> details,
        Instant timestamp) {

    public static ApiErrorResponse of(ErrorCode code, String message, String path, Map<String, Object> details) {
        return new ApiErrorResponse(false, code.name(), message, path, details, Instant.now());
    }
}
