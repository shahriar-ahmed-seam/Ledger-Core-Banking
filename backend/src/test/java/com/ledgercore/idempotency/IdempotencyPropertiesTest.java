package com.ledgercore.idempotency;

import com.ledgercore.common.error.DomainException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for idempotency rules.
 */
class IdempotencyPropertiesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);

    private IdempotencyKeyRecord completed(String key, String fp, UUID txn) {
        IdempotencyKeyRecord r = new IdempotencyKeyRecord(key, fp, "COMPLETED", Instant.now(CLOCK));
        r.setTransactionId(txn);
        return r;
    }

    // Feature: ledger-core-banking, Property 28: Idempotency-key format is enforced
    @Property(tries = 300)
    void property28_idempotencyKeyFormatEnforced(@ForAll("anyKey") String key) {
        boolean valid = key != null && key.length() >= 1 && key.length() <= 128;
        if (valid) {
            IdempotencyService.validateKeyFormat(key); // no throw
        } else {
            assertThatThrownBy(() -> IdempotencyService.validateKeyFormat(key))
                    .isInstanceOf(DomainException.class);
        }
    }

    @net.jqwik.api.Provide
    Arbitrary<String> anyKey() {
        Arbitrary<String> tooLong = Arbitraries.strings().withCharRange('a', 'z').ofMinLength(129).ofMaxLength(140);
        Arbitrary<String> ok = Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(128);
        Arbitrary<String> empty = Arbitraries.just("");
        return Arbitraries.oneOf(ok, tooLong, empty);
    }

    // Feature: ledger-core-banking, Property 27: Idempotency-key conflicts are rejected
    @Property(tries = 300)
    void property27_idempotencyKeyConflictsRejected(@ForAll @StringLength(min = 1, max = 64) String key,
                                                    @ForAll @IntRange(min = 0, max = 1000) int aSeed,
                                                    @ForAll @IntRange(min = 0, max = 1000) int bSeed) {
        String fpRecorded = IdempotencyService.fingerprint(
                UUID.randomUUID(), UUID.randomUUID(), aSeed + ".00", "USD");
        String fpNew = IdempotencyService.fingerprint(
                UUID.randomUUID(), UUID.randomUUID(), bSeed + ".50", "EUR");

        IdempotencyKeyRecord existing = completed(key, fpRecorded, UUID.randomUUID());
        IdempotencyResult result = IdempotencyService.decide(Optional.of(existing), fpNew);

        if (fpRecorded.equals(fpNew)) {
            assertThat(result.outcome()).isEqualTo(IdempotencyResult.Outcome.REPLAY);
        } else {
            assertThat(result.outcome()).isEqualTo(IdempotencyResult.Outcome.CONFLICT_MISMATCH);
        }
    }

    // Feature: ledger-core-banking, Property 26: Idempotent replay yields one transaction
    @Property(tries = 200)
    void property26_idempotentReplayYieldsOneTransaction(@ForAll @StringLength(min = 1, max = 64) String key,
                                                         @ForAll @IntRange(min = 2, max = 8) int retries) {
        // Stateful in-memory store backing a mocked repository.
        Map<String, IdempotencyKeyRecord> store = new HashMap<>();
        IdempotencyKeyRepository repo = mock(IdempotencyKeyRepository.class);
        when(repo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
        when(repo.saveAndFlush(any(IdempotencyKeyRecord.class))).thenAnswer(inv -> {
            IdempotencyKeyRecord r = inv.getArgument(0);
            store.put(r.getIdempotencyKey(), r);
            return r;
        });
        when(repo.save(any(IdempotencyKeyRecord.class))).thenAnswer(inv -> {
            IdempotencyKeyRecord r = inv.getArgument(0);
            store.put(r.getIdempotencyKey(), r);
            return r;
        });

        IdempotencyService service = new IdempotencyService(repo, CLOCK, Duration.ofHours(24));
        String fp = IdempotencyService.fingerprint(UUID.randomUUID(), UUID.randomUUID(), "10.00", "USD");

        // First call begins; we complete it with a single transaction id.
        IdempotencyResult first = service.begin(key, fp);
        assertThat(first.isBegun()).isTrue();
        UUID theTransaction = UUID.randomUUID();
        service.complete(key, theTransaction);

        // Every subsequent identical call replays the original transaction; none begins anew.
        for (int i = 0; i < retries; i++) {
            IdempotencyResult again = service.begin(key, fp);
            assertThat(again.outcome()).isEqualTo(IdempotencyResult.Outcome.REPLAY);
            assertThat(again.transactionId()).isEqualTo(theTransaction);
        }
    }
}
