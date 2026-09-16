package com.possaas.common.web;

import com.possaas.common.api.ApiError;
import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Translates every exception into the single {@link ApiError} envelope.
 *
 * <p>Two rules: expected failures are logged at debug and returned with a message the
 * client may display, while unexpected ones are logged with a stack trace and returned as
 * a generic message plus the request id. Internal details never reach the response.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        log.debug("Handled {}: {}", ex.code(), ex.getMessage());
        return build(ex.code(), ex.getMessage(), ex.details(), List.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBeanValidation(MethodArgumentNotValidException ex,
                                                        HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(
                        error.getField(),
                        error.getDefaultMessage(),
                        error.getRejectedValue()))
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, "Some fields need attention",
                Map.of(), fieldErrors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                             HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new ApiError.FieldError(
                        violation.getPropertyPath().toString(),
                        violation.getMessage(),
                        violation.getInvalidValue()))
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, "Some fields need attention",
                Map.of(), fieldErrors, request);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiError> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        return build(ErrorCode.VALIDATION_FAILED, "The request could not be read",
                Map.of("reason", ex.getMessage()), List.of(), request);
    }

    /**
     * Database constraints are the last line of defence. Reaching one usually means a
     * service-level check is missing, so it is logged as a warning even though the client
     * sees an ordinary conflict.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        log.warn("Database constraint rejected a write: {}", rootMessage(ex));
        return build(ErrorCode.CONFLICT,
                "That change conflicts with existing data",
                Map.of(), List.of(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                         HttpServletRequest request) {
        return build(ErrorCode.OPTIMISTIC_LOCK,
                "Someone else changed this record while you were editing it. Reload and try again.",
                Map.of(), List.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                       HttpServletRequest request) {
        return build(ErrorCode.PERMISSION_DENIED,
                "You do not have permission to do that",
                Map.of(), List.of(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex,
                                                         HttpServletRequest request) {
        return build(ErrorCode.UNAUTHENTICATED, "Please sign in again",
                Map.of(), List.of(), request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleNoHandler(NoHandlerFoundException ex,
                                                    HttpServletRequest request) {
        return build(ErrorCode.NOT_FOUND, "No such endpoint", Map.of(), List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        String requestId = RequestContextFilter.currentRequestId(request);
        log.error("Unhandled exception on {} {} (requestId={})",
                request.getMethod(), request.getRequestURI(), requestId, ex);
        return build(ErrorCode.INTERNAL_ERROR,
                "Something went wrong on our side. Quote reference " + requestId + " to support.",
                Map.of(), List.of(), request);
    }

    private ResponseEntity<ApiError> build(ErrorCode code,
                                           String message,
                                           Map<String, Object> details,
                                           List<ApiError.FieldError> fieldErrors,
                                           HttpServletRequest request) {
        HttpStatus status = code.status();
        ApiError body = new ApiError(
                code.name(),
                message,
                details.isEmpty() ? null : details,
                fieldErrors.isEmpty() ? null : fieldErrors,
                request.getRequestURI(),
                RequestContextFilter.currentRequestId(request),
                Instant.now());
        return ResponseEntity.status(status).body(body);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage();
    }
}
