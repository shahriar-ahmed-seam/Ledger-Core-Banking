package com.ledgercore.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ledgercore.common.error.FieldError;

import java.util.List;

/**
 * The error body within an error response envelope (Requirement 12.4).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, List<FieldError> fields) {
}
