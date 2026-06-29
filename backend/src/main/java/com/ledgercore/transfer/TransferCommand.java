package com.ledgercore.transfer;

import java.util.UUID;

/**
 * A request to move money between two accounts.
 *
 * @param actorId        the authenticated user performing the transfer (for audit)
 * @param idempotencyKey the client-supplied idempotency key
 * @param sourceId       the source account
 * @param destinationId  the destination account
 * @param amount         the amount as a decimal string
 * @param currency       the ISO 4217 currency code
 */
public record TransferCommand(UUID actorId, String idempotencyKey, UUID sourceId,
                              UUID destinationId, String amount, String currency) {
}
