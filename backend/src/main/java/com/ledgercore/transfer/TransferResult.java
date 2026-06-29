package com.ledgercore.transfer;

import java.util.UUID;

/**
 * The result of a transfer: the posted transaction identifier and whether this response was
 * an idempotent replay of a prior request (Requirements 6.1, 8.2).
 */
public record TransferResult(UUID transactionId, boolean replayed) {
}
