package com.ledgercore.idempotency;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyRecord, String> {

    /**
     * Deletes idempotency records older than the cutoff (retention cleanup, Requirement 8.6).
     */
    @Modifying
    @Query("DELETE FROM IdempotencyKeyRecord k WHERE k.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
