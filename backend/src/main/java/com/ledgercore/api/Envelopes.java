package com.ledgercore.api;

/**
 * Response envelopes. A success response carries only {@code data} and never error fields;
 * an error response carries only {@code error} (Requirements 12.4, 12.5).
 */
public final class Envelopes {

    private Envelopes() {
    }

    /** Success envelope: {@code { "data": ... }}. */
    public record Success<T>(T data) {
    }

    /** Error envelope: {@code { "error": { code, message, fields } }}. */
    public record Error(ApiError error) {
    }

    public static <T> Success<T> ok(T data) {
        return new Success<>(data);
    }
}
