package com.ledgercore.common.error;

import org.springframework.http.HttpStatus;

/**
 * The defined, machine-readable error-code catalog (design "Error-code catalog").
 *
 * <p>Requirement 12.4: every error response carries a code from this defined set.</p>
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST),
    AUTHENTICATION_ERROR(HttpStatus.UNAUTHORIZED),
    AUTHORIZATION_ERROR(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY),
    ACCOUNT_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY),
    LEDGER_IMBALANCE(HttpStatus.UNPROCESSABLE_ENTITY),
    RETRYABLE_CONFLICT(HttpStatus.SERVICE_UNAVAILABLE),
    PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
