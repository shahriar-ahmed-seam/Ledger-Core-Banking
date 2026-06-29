package com.ledgercore.audit;

import com.ledgercore.authz.AccessAction;
import com.ledgercore.authz.RbacPolicy;
import com.ledgercore.common.error.DomainException;
import com.ledgercore.common.error.ErrorCode;
import com.ledgercore.user.Role;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for the audit trail. Deterministic ordering and append-only DB
 * behavior are additionally covered by the Testcontainers integration tests.
 */
class AuditPropertiesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-03-03T03:03:03.123Z"), ZoneOffset.UTC);

    @Provide
    Arbitrary<AuditAction> action() {
        return Arbitraries.of(AuditAction.class);
    }

    @Provide
    Arbitrary<Role> role() {
        return Arbitraries.of(Role.class);
    }

    // Feature: ledger-core-banking, Property 33: Sensitive actions produce complete audit entries atomically
    @Property(tries = 200)
    void property33_sensitiveActionsProduceCompleteEntriesAtomically(@ForAll("action") AuditAction action,
                                                                     @ForAll boolean success) {
        AuditEntryRepository repo = mock(AuditEntryRepository.class);
        when(repo.save(any(AuditEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        AuditService service = new AuditService(repo, CLOCK);

        UUID actor = UUID.randomUUID();
        String target = UUID.randomUUID().toString();
        service.record(actor, action, target, success, "detail");

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(repo).save(captor.capture());
        AuditEntry e = captor.getValue();
        assertThat(e.getActorId()).isEqualTo(actor);
        assertThat(e.getActionType()).isEqualTo(action.name());
        assertThat(e.getTargetId()).isEqualTo(target);
        assertThat(e.getOutcome()).isEqualTo(success ? "SUCCESS" : "FAILURE");
        // Millisecond-precision timestamp.
        assertThat(e.getOccurredAt()).isEqualTo(Instant.parse("2025-03-03T03:03:03.123Z"));

        // If the audit entry cannot be persisted, the action is not reported successful:
        // record() surfaces a PERSISTENCE_ERROR, which rolls back the enclosing action.
        AuditEntryRepository failing = mock(AuditEntryRepository.class);
        doThrow(new RuntimeException("db down")).when(failing).save(any(AuditEntry.class));
        AuditService failingService = new AuditService(failing, CLOCK);
        assertThatThrownBy(() -> failingService.record(actor, action, target, success, "d"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).code()).isEqualTo(ErrorCode.PERSISTENCE_ERROR));
    }

    // Feature: ledger-core-banking, Property 34: The audit log is append-only and admin-queryable in deterministic order
    @Property(tries = 200)
    void property34_auditAppendOnlyAndAdminOnly(@ForAll("role") Role role) throws Exception {
        // Admin-only access: only ADMIN may view audit entries.
        assertThat(RbacPolicy.permits(role, AccessAction.VIEW_AUDIT, false))
                .isEqualTo(role == Role.ADMIN);

        // Append-only at the model level: no mutator methods, columns are non-updatable, and
        // the repository exposes neither update nor delete operations.
        for (var m : AuditEntry.class.getMethods()) {
            assertThat(m.getName()).doesNotStartWith("set");
        }
        for (var f : new String[]{"actionType", "actorId", "targetId", "outcome", "occurredAt"}) {
            var column = AuditEntry.class.getDeclaredField(f).getAnnotation(jakarta.persistence.Column.class);
            assertThat(column.updatable()).isFalse();
        }
        for (var m : AuditEntryRepository.class.getMethods()) {
            assertThat(m.getName().toLowerCase()).doesNotContain("delete");
        }
    }
}
