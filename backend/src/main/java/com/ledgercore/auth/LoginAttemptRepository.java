package com.ledgercore.auth;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    /**
     * Counts failed attempts for an email since the given instant (lockout window, R2.9).
     */
    @Query("""
            SELECT COUNT(a) FROM LoginAttempt a
            WHERE a.emailNormalized = :email
              AND a.success = false
              AND a.attemptedAt >= :since
            """)
    long countRecentFailures(@Param("email") String email, @Param("since") Instant since);
}
