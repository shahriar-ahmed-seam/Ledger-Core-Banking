package com.ledgercore.api;

import com.ledgercore.common.error.DomainException;
import com.ledgercore.common.error.ErrorCode;
import com.ledgercore.common.error.FieldError;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;

import java.util.List;

/**
 * Translates exceptions into the uniform error envelope with a machine-readable code and a
 * human-readable message (Requirements 12.1, 12.4).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Envelopes.Error> handleDomain(DomainException ex) {
        return build(ex.code(), ex.getMessage(), ex.fields());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Envelopes.Error> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(),
                        fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, "Request validation failed.", fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    public ResponseEntity<Envelopes.Error> handleMalformed(Exception ex) {
        return build(ErrorCode.VALIDATION_ERROR, "Malformed or incomplete request.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Envelopes.Error> handleUnexpected(Exception ex) {
        // Never leak internals; map to a generic persistence/internal error.
        return build(ErrorCode.PERSISTENCE_ERROR, "An unexpected error occurred.", List.of());
    }

    private ResponseEntity<Envelopes.Error> build(ErrorCode code, String message,
                                                  List<FieldError> fields) {
        ApiError error = new ApiError(code.name(), message, fields == null ? List.of() : fields);
        return ResponseEntity.status(code.status()).body(new Envelopes.Error(error));
    }
}
