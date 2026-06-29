package com.ledgercore.audit;

import com.ledgercore.common.error.DomainException;
import com.ledgercore.common.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Append-only audit trail (Requirement 11). Audit entries are written inside the same
 * transaction as the action they describe, so an action cannot be reported successful
 * unless its audit row also commits (Requirement 11.6).
 */
@Service
public class AuditService {

    private final AuditEntryRepository repository;
    private final Clock clock;

    public AuditService(AuditEntryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Records an audit entry within the caller's transaction (Requirements 11.1, 11.6).
     *
     * @throws DomainException (PERSISTENCE_ERROR) if the entry cannot be persisted, which
     *                         rolls back the enclosing action.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, AuditAction action, String targetId, boolean success,
                       String detail) {
        try {
            Instant occurredAt = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
            repository.save(new AuditEntry(actorId, action.name(), targetId,
                    success ? "SUCCESS" : "FAILURE", detail, occurredAt));
        } catch (RuntimeException e) {
            throw new DomainException(ErrorCode.PERSISTENCE_ERROR,
                    "Failed to persist audit entry for action " + action);
        }
    }

    /**
     * Records an audit entry in its own independent transaction. Used for events (such as a
     * failed login) that must be recorded even when the surrounding request fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(UUID actorId, AuditAction action, String targetId,
                                  boolean success, String detail) {
        Instant occurredAt = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        repository.save(new AuditEntry(actorId, action.name(), targetId,
                success ? "SUCCESS" : "FAILURE", detail, occurredAt));
    }

    /**
     * Returns a deterministically ordered page of audit entries (Requirement 11.3). The
     * caller is responsible for enforcing ADMIN authorization before invoking this.
     */
    @Transactional(readOnly = true)
    public List<AuditEntry> query(Instant from, Instant to, Instant afterTs, long afterId, int pageSize) {
        Instant fromTs = from == null ? Instant.EPOCH : from;
        Instant toTs = to == null ? Instant.now(clock).plus(3650, ChronoUnit.DAYS) : to;
        try {
            return repository.findPage(fromTs, toTs,
                    afterTs == null ? Instant.EPOCH : afterTs, afterId,
                    PageRequest.of(0, pageSize));
        } catch (RuntimeException e) {
            // Requirement 11.5: never return a partial or unordered result set on error.
            throw new DomainException(ErrorCode.PERSISTENCE_ERROR,
                    "Failed to retrieve audit entries.");
        }
    }
}
