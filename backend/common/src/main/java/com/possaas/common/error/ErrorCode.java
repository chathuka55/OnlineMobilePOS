package com.possaas.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error identifiers.
 *
 * <p>Clients branch on these rather than on HTTP status or message text, so a POS
 * terminal can react specifically to, say, {@link #INSUFFICIENT_STOCK} without
 * parsing prose.
 */
public enum ErrorCode {

    // --- generic ---
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    OPTIMISTIC_LOCK(HttpStatus.CONFLICT),

    // --- auth ---
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    TOKEN_REUSED(HttpStatus.UNAUTHORIZED),
    STEP_UP_REQUIRED(HttpStatus.FORBIDDEN),
    PERMISSION_DENIED(HttpStatus.FORBIDDEN),

    // --- tenancy & subscription ---
    TENANT_NOT_FOUND(HttpStatus.NOT_FOUND),
    TENANT_SUSPENDED(HttpStatus.FORBIDDEN),
    TENANT_CONTEXT_MISSING(HttpStatus.INTERNAL_SERVER_ERROR),
    SUBSCRIPTION_INACTIVE(HttpStatus.PAYMENT_REQUIRED),
    SUBSCRIPTION_READ_ONLY(HttpStatus.PAYMENT_REQUIRED),
    FEATURE_NOT_IN_PLAN(HttpStatus.PAYMENT_REQUIRED),
    QUOTA_EXCEEDED(HttpStatus.PAYMENT_REQUIRED),

    // --- inventory ---
    INSUFFICIENT_STOCK(HttpStatus.UNPROCESSABLE_ENTITY),
    SERIAL_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    SERIAL_COUNT_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY),
    SERIAL_ALREADY_EXISTS(HttpStatus.CONFLICT),
    ITEM_NOT_SELLABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    DUPLICATE_SKU(HttpStatus.CONFLICT),
    DUPLICATE_BARCODE(HttpStatus.CONFLICT),

    // --- sales ---
    CART_EMPTY(HttpStatus.UNPROCESSABLE_ENTITY),
    CART_ALREADY_CONVERTED(HttpStatus.CONFLICT),
    BILL_ALREADY_VOIDED(HttpStatus.CONFLICT),
    BILL_NOT_REFUNDABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    REFUND_EXCEEDS_BILL(HttpStatus.UNPROCESSABLE_ENTITY),
    PAYMENT_EXCEEDS_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY),
    DISCOUNT_EXCEEDS_LINE(HttpStatus.UNPROCESSABLE_ENTITY),
    PRICE_BELOW_MINIMUM(HttpStatus.UNPROCESSABLE_ENTITY),

    // --- credit ---
    CREDIT_NOTE_EXHAUSTED(HttpStatus.UNPROCESSABLE_ENTITY),
    CREDIT_NOTE_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY),
    CREDIT_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY),

    // --- repairs ---
    INVALID_STATUS_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY),
    REPAIR_NOT_DELIVERABLE(HttpStatus.UNPROCESSABLE_ENTITY),

    // --- documents ---
    SEQUENCE_LOCK_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE),

    // --- storage ---
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
