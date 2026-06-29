package com.ledgercore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ledger-Core-Banking application entry point.
 *
 * <p>A production-grade core banking backend implementing double-entry bookkeeping,
 * concurrency-safe money movement via PostgreSQL row-level locking, JWT/RBAC security,
 * and an append-only audit trail.</p>
 */
@SpringBootApplication
@EnableScheduling
public class LedgerCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerCoreApplication.class, args);
    }
}
