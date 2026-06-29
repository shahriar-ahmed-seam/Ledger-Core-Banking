package com.ledgercore.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A bank Account holding a balance in a single, immutable currency (Requirement 4).
 *
 * <p>The authoritative balance is always derived from posted ledger entries
 * (Requirement 4.4); {@code balanceCache} is a reconcilable convenience cache updated
 * under the account's row lock during money movement.</p>
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    @Column(name = "overdraft_limit", nullable = false)
    private BigDecimal overdraftLimit;

    @Column(name = "balance_cache", nullable = false)
    private BigDecimal balanceCache;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
    }

    public Account(UUID id, UUID ownerId, String currency, AccountStatus status,
                   BigDecimal overdraftLimit, BigDecimal balanceCache, Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.currency = currency;
        this.status = status;
        this.overdraftLimit = overdraftLimit;
        this.balanceCache = balanceCache;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getCurrency() {
        return currency;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public BigDecimal getOverdraftLimit() {
        return overdraftLimit;
    }

    public BigDecimal getBalanceCache() {
        return balanceCache;
    }

    public void setBalanceCache(BigDecimal balanceCache) {
        this.balanceCache = balanceCache;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
}
