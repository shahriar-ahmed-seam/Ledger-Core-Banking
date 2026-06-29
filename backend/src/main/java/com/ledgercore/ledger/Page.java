package com.ledgercore.ledger;

import java.util.List;

/**
 * A page of results plus an opaque cursor for the next page (null when exhausted).
 */
public record Page<T>(List<T> items, String nextCursor) {
}
