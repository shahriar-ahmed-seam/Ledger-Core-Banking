package com.ledgercore.common.error;

/**
 * A single field-level validation problem (Requirement 12.1).
 *
 * @param field the offending field name
 * @param issue a human-readable description of the problem
 */
public record FieldError(String field, String issue) {
}
