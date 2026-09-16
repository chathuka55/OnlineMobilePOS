package com.possaas.common.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The single error envelope returned by every endpoint.
 *
 * @param code      stable {@code ErrorCode} name to branch on
 * @param message   human-readable summary, safe to show a cashier
 * @param details   error-specific context, for example available stock
 * @param fieldErrors per-field validation failures
 * @param requestId correlation id, also present in the logs and the response header
 */
public record ApiError(
        String code,
        String message,
        Map<String, Object> details,
        List<FieldError> fieldErrors,
        String path,
        String requestId,
        Instant timestamp
) {

    public record FieldError(String field, String message, Object rejectedValue) {
    }
}
