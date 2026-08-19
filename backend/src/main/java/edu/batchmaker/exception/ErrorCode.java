package edu.batchmaker.exception;

import org.springframework.http.HttpStatus;

/** Stable machine-readable error codes returned to the frontend (spec section 42). */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN),

    TIMETABLE_CONFLICT(HttpStatus.CONFLICT),
    TIMETABLE_NOT_PUBLISHED(HttpStatus.CONFLICT),
    TIMETABLE_ALREADY_PUBLISHED(HttpStatus.CONFLICT),
    TIMETABLE_HAS_VIOLATIONS(HttpStatus.UNPROCESSABLE_ENTITY),
    NO_FEASIBLE_SCHEDULE(HttpStatus.UNPROCESSABLE_ENTITY),
    SOLVER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    SOLVER_FAILED(HttpStatus.BAD_GATEWAY),

    BATCH_GENERATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY),
    CAPACITY_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY),
    NO_ALTERNATIVE_SLOT(HttpStatus.UNPROCESSABLE_ENTITY),
    RESCHEDULE_STATE_INVALID(HttpStatus.CONFLICT),

    IMPORT_FAILED(HttpStatus.BAD_REQUEST),
    EXPORT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
    ILLEGAL_STATE(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
