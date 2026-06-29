package com.ledgercore.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByOwnerId(UUID ownerId);

    /**
     * Acquires a PostgreSQL row-level lock ({@code SELECT ... FOR UPDATE}) on the account,
     * held until the surrounding transaction commits or aborts.
     *
     * <p>Requirements: 7.1 (acquire row lock before reading balance), 7.2 (FIFO waiting).</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}
