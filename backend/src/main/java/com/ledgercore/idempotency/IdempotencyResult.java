package com.ledgercore.idempotency;

import java.util.UUID;

/**
 * Outcome of an idempotency-key claim attempt (Requirement 8).
 */
public record IdempotencyResult(Outcome outcome, UUID transactionId) {

    public enum Outcome {
        /** A new key was claimed; the caller should perform the operation (R8.1). */
        BEGUN,
        /** The key matches a completed request with identical parameters; replay (R8.2). */
        REPLAY,
        /** The key matches an in-progress request; reject as conflict (R8.3). */
        CONFLICT_IN_PROGRESS,
        /** The key matches a recorded request with different parameters; conflict (R8.4). */
        CONFLICT_MISMATCH
    }

    public static IdempotencyResult begun() {
        return new IdempotencyResult(Outcome.BEGUN, null);
    }

    public static IdempotencyResult replay(UUID txnId) {
        return new IdempotencyResult(Outcome.REPLAY, txnId);
    }

    public static IdempotencyResult conflictInProgress() {
        return new IdempotencyResult(Outcome.CONFLICT_IN_PROGRESS, null);
    }

    public static IdempotencyResult conflictMismatch() {
        return new IdempotencyResult(Outcome.CONFLICT_MISMATCH, null);
    }

    public boolean isBegun() {
        return outcome == Outcome.BEGUN;
    }
}
