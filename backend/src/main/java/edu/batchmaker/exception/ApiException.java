package edu.batchmaker.exception;

import java.util.Map;
import lombok.Getter;

/** Application error carrying a stable {@link ErrorCode} and a human message. */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Map<String, Object> details;

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ApiException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public static ApiException notFound(String entity, Object id) {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, entity + " " + id + " was not found.");
    }

    public static ApiException duplicate(String message) {
        return new ApiException(ErrorCode.DUPLICATE_RESOURCE, message);
    }

    public static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
