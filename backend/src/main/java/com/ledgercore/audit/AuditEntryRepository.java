package com.ledgercore.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Append-only audit repository: insert + read only, never update or delete
 * (Requirements 11.2, 11.4).
 */
public interface AuditEntryRepository extends Repository<AuditEntry, Long> {

    AuditEntry save(AuditEntry entry);

    /**
     * Returns audit entries ordered deterministically by (occurred_at, id) ascending,
     * within an inclusive time range, after a keyset cursor (Requirement 11.3).
     */
    @Query("""
            SELECT a FROM AuditEntry a
            WHERE a.occurredAt >= :fromTs
              AND a.occurredAt <= :toTs
              AND (a.occurredAt > :afterTs OR (a.occurredAt = :afterTs AND a.id > :afterId))
            ORDER BY a.occurredAt ASC, a.id ASC
            """)
    List<AuditEntry> findPage(@Param("fromTs") Instant fromTs,
                              @Param("toTs") Instant toTs,
                              @Param("afterTs") Instant afterTs,
                              @Param("afterId") long afterId,
                              Pageable pageable);
}
