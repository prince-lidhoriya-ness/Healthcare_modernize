package com.ivirma.clinic.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.ClassUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;

import java.sql.SQLException;

/**
 * Maps exceptions to the documented error body {status, error, message, detail, timestamp} (docs/api.md).
 *
 * <p>STORY-010: no response carries a stack trace, an exception class name or exception text, so
 * {@code detail} is always null. Malformed client input is a 400; anything unexpected is a generic 500
 * that is logged server-side by type and stack frames only.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String UNEXPECTED_ERROR_MESSAGE = "An unexpected error occurred";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Upper bound on the logged cause chain (guards against pathological or cyclic chains). */
    private static final int MAX_LOGGED_CAUSES = 16;

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = "Validation failed";
        FieldError fieldError = ex.getBindingResult().getFieldError();
        if (fieldError != null) {
            message = fieldError.getField() + ": " + fieldError.getDefaultMessage();
        }
        return build(HttpStatus.BAD_REQUEST, message);
    }

    /** Malformed JSON, an unknown enum value or a bad date. The parser text names Java types, so it stays out. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable() {
        return build(HttpStatus.BAD_REQUEST, "Request body is invalid");
    }

    /** A path variable or query parameter that cannot be converted, e.g. GET /api/patients/abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getName() + ": " + expectedValue(ex.getRequiredType()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getParameterName() + ": must be provided");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error handling {} {}", request.getMethod(), routeOf(request), withoutMessages(ex));
        return build(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR_MESSAGE);
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String message) {
        ApiError body = new ApiError(status.value(), status.getReasonPhrase(), message, null);
        return new ResponseEntity<>(body, status);
    }

    private static String expectedValue(Class<?> requiredType) {
        Class<?> type = requiredType == null ? Object.class : ClassUtils.resolvePrimitiveIfNecessary(requiredType);
        if (Number.class.isAssignableFrom(type)) {
            return "must be a number";
        }
        if (Boolean.class.equals(type)) {
            return "must be true or false";
        }
        return "has an invalid value";
    }

    /** The matched route pattern (e.g. /api/patients/{id}): no ids, query strings or other request input. */
    private static String routeOf(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern == null ? "(no route)" : pattern.toString();
    }

    /**
     * A copy of the exception chain with each exception's type and stack frames but without its message:
     * messages can echo patient data, e.g. PostgreSQL's "Failing row contains (...)" on the RULE-024/025
     * integrity edges. SQL states are kept because they carry no data.
     */
    private static Throwable withoutMessages(Throwable ex) {
        return redact(ex, 0);
    }

    private static Throwable redact(Throwable ex, int depth) {
        Throwable cause = ex.getCause();
        Throwable redactedCause = cause == null || depth >= MAX_LOGGED_CAUSES ? null : redact(cause, depth + 1);
        String summary = ex.getClass().getName();
        if (ex instanceof SQLException sql && sql.getSQLState() != null) {
            summary += " SQLState=" + sql.getSQLState();
        }
        RedactedException copy = new RedactedException(summary, redactedCause);
        copy.setStackTrace(ex.getStackTrace());
        return copy;
    }

    /** Stands in for a logged exception: its message is the original type, the original message is withheld. */
    private static final class RedactedException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        RedactedException(String originalType, Throwable cause) {
            super(originalType, cause, false, true);
        }
    }
}
