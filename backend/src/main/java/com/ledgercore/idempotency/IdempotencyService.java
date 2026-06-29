package com.ledgercore.idempotency;

import com.ledgercore.common.error.DomainException;
import com.ledgercore.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotent money-movement support (Requirement 8). The {@code idempotency_keys} primary
 * key provides the single-winner guarantee: under concurrent duplicates only one INSERT of
 * a given key can succeed.
 */
@Service
public class IdempotencyService {

    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String COMPLETED = "COMPLETED";

    private final IdempotencyKeyRepository repository;
    private final Clock clock;
    private final Duration retention;

    public IdempotencyService(IdempotencyKeyRepository repository, Clock clock,
                              @Value("${ledger.idempotency.retention:PT24H}") Duration retention) {
        this.repository = repository;
        this.clock = clock;
        this.retention = retention;
    }

    /**
     * Validates an idempotency key's format (Requirement 8.5).
     *
     * @throws DomainException (INVALID_IDEMPOTENCY_KEY) if missing or not 1..128 characters.
     */
    public static void validateKeyFormat(String key) {
        if (key == null || key.isEmpty() || key.length() > 128) {
            throw new DomainException(ErrorCode.INVALID_IDEMPOTENCY_KEY,
                    "Idempotency-Key must be a string of 1 to 128 characters.");
        }
    }

    /**
     * Computes the request fingerprint from the money-movement parameters.
     */
    public static String fingerprint(UUID source, UUID destination, String amount, String currency) {
        String raw = source + "|" + destination + "|" + amount + "|" + currency;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Pure decision over an existing record (if any) for a given fingerprint. Extracted so
     * the idempotency rules can be exercised directly by property tests.
     */
    public static IdempotencyResult decide(Optional<IdempotencyKeyRecord> existing, String fingerprint) {
        if (existing.isEmpty()) {
            return IdempotencyResult.begun();
        }
        IdempotencyKeyRecord record = existing.get();
        if (!record.getRequestFingerprint().equals(fingerprint)) {
            return IdempotencyResult.conflictMismatch();   // R8.4
        }
        if (COMPLETED.equals(record.getStatus())) {
            return IdempotencyResult.replay(record.getTransactionId());  // R8.2
        }
        return IdempotencyResult.conflictInProgress();      // R8.3
    }

    /**
     * Attempts to claim the key for a new operation. Must run inside the caller's transaction.
     */
    @Transactional
    public IdempotencyResult begin(String key, String fingerprint) {
        validateKeyFormat(key);
        IdempotencyResult decision = decide(repository.findById(key), fingerprint);
        if (!decision.isBegun()) {
            return decision;
        }
        try {
            repository.saveAndFlush(new IdempotencyKeyRecord(
                    key, fingerprint, IN_PROGRESS, Instant.now(clock)));
            return IdempotencyResult.begun();
        } catch (DataIntegrityViolationException concurrentInsert) {
            // Another transaction won the race to claim this key (single-winner guarantee).
            // The current transaction is now rollback-only; surface a conflict so it aborts
            // cleanly and the caller can retry safely with the same key (R8.3).
            throw new DomainException(ErrorCode.CONFLICT,
                    "A concurrent request is already processing this idempotency key.");
        }
    }

    /**
     * Marks a previously-begun key COMPLETED with its resulting transaction id (R8.1).
     */
    @Transactional
    public void complete(String key, UUID transactionId) {
        IdempotencyKeyRecord record = repository.findById(key)
                .orElseThrow(() -> new IllegalStateException("Idempotency key vanished: " + key));
        record.setStatus(COMPLETED);
        record.setTransactionId(transactionId);
        record.setCompletedAt(Instant.now(clock));
        repository.save(record);
    }

    /**
     * Removes idempotency records older than the retention window (Requirement 8.6 keeps
     * recent keys; this only deletes those beyond it).
     */
    @Transactional
    public int purgeExpired() {
        Instant cutoff = Instant.now(clock).minus(retention);
        return repository.deleteOlderThan(cutoff);
    }

    public Duration retention() {
        return retention;
    }
}
