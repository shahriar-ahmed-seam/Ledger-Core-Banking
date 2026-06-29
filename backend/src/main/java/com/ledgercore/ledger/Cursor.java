package com.ledgercore.ledger;

import com.ledgercore.common.error.DomainException;
import com.ledgercore.common.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * An opaque keyset pagination cursor encoding a (postedAt, id) position.
 *
 * <p>Requirements: 10.4 (cursor), 10.7 (invalid cursor rejected).</p>
 */
public record Cursor(Instant postedAt, long id) {

    public String encode() {
        String raw = postedAt.toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a cursor string, or returns the beginning-of-stream position when {@code null}.
     *
     * @throws DomainException (INVALID_CURSOR) if the string is malformed (Requirement 10.7).
     */
    public static Cursor decodeOrStart(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return new Cursor(Instant.EPOCH, 0L);
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int sep = raw.indexOf(':');
            if (sep <= 0) {
                throw new IllegalArgumentException("missing separator");
            }
            long millis = Long.parseLong(raw.substring(0, sep));
            long id = Long.parseLong(raw.substring(sep + 1));
            return new Cursor(Instant.ofEpochMilli(millis), id);
        } catch (RuntimeException e) {
            throw new DomainException(ErrorCode.INVALID_CURSOR, "Pagination cursor is invalid.");
        }
    }
}
