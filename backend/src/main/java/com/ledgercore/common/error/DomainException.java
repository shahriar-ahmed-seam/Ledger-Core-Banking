package com.ledgercore.common.error;

import java.util.List;

/**
 * Base type for all domain errors. Carries a machine-readable {@link ErrorCode}, a
 * human-readable message, and optional field-level details.
 *
 * <p>Requirements: 12.4 (code + message), 12.1 (field-level detail).</p>
 */
public class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final transient List<FieldError> fields;

    public DomainException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public DomainException(ErrorCode code, String message, List<FieldError> fields) {
        super(message);
        this.code = code;
        this.fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldError> fields() {
        return fields;
    }

    // --- Convenience factories for the common cases ---

    public static DomainException validation(String message, List<FieldError> fields) {
        return new DomainException(ErrorCode.VALIDATION_ERROR, message, fields);
    }

    public static DomainException validation(String message, String field, String issue) {
        return new DomainException(ErrorCode.VALIDATION_ERROR, message,
                List.of(new FieldError(field, issue)));
    }

    public static DomainException notFound(String message) {
        return new DomainException(ErrorCode.NOT_FOUND, message);
    }

    public static DomainException conflict(String message) {
        return new DomainException(ErrorCode.CONFLICT, message);
    }

    public static DomainException authorization(String message) {
        return new DomainException(ErrorCode.AUTHORIZATION_ERROR, message);
    }

    public static DomainException authentication(String message) {
        return new DomainException(ErrorCode.AUTHENTICATION_ERROR, message);
    }

    public static DomainException insufficientFunds(String message) {
        return new DomainException(ErrorCode.INSUFFICIENT_FUNDS, message);
    }

    public static DomainException accountClosed(String message) {
        return new DomainException(ErrorCode.ACCOUNT_CLOSED, message);
    }

    public static DomainException ledgerImbalance(String message) {
        return new DomainException(ErrorCode.LEDGER_IMBALANCE, message);
    }

    public static DomainException retryable(String message) {
        return new DomainException(ErrorCode.RETRYABLE_CONFLICT, message);
    }
}
