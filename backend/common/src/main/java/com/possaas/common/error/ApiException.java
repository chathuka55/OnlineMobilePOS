package com.possaas.common.error;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for every deliberately-thrown application error.
 *
 * <p>Carries an {@link ErrorCode} and an optional detail map that the client can use
 * to render a precise message, for example the available quantity alongside an
 * {@link ErrorCode#INSUFFICIENT_STOCK}.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details = new LinkedHashMap<>();

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }

    public ApiException with(String key, Object value) {
        details.put(key, value);
        return this;
    }

    // --- factories for the shapes used most often -------------------------------

    public static ApiException notFound(String entity, Object id) {
        return new ApiException(ErrorCode.NOT_FOUND, entity + " not found")
                .with("entity", entity)
                .with("id", String.valueOf(id));
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    public static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, message);
    }

    public static ApiException of(ErrorCode code, String message) {
        return new ApiException(code, message);
    }
}
