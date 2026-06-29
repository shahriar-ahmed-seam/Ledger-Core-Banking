package com.ledgercore.config;

import com.ledgercore.account.Account;
import com.ledgercore.account.AccountService;
import com.ledgercore.auth.AuthService;
import com.ledgercore.ledger.LedgerService;
import com.ledgercore.ledger.PostingLine;
import com.ledgercore.user.Role;
import com.ledgercore.user.User;
import com.ledgercore.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Seeds a demo ADMIN and a customer with funded accounts. Enabled only when
 * {@code ledger.seed.enabled=true} (the docker-compose demo profile). Idempotent across
 * restarts.
 */
@Configuration
@ConditionalOnProperty(name = "ledger.seed.enabled", havingValue = "true")
public class DevDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    @Bean
    ApplicationRunner seed(AuthService authService, AccountService accountService,
                           LedgerService ledgerService, UserRepository userRepository) {
        return args -> {
            if (userRepository.findByEmailNormalized("admin@ledger.local").isPresent()) {
                log.info("Seed data already present; skipping.");
                return;
            }

            // Admin (registered as CUSTOMER then promoted).
            UUID adminId = authService.register("admin@ledger.local", "AdminPass123!");
            User admin = userRepository.findById(adminId).orElseThrow();
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);

            // Demo customer with two BDT accounts.
            UUID demoId = authService.register("demo@ledger.local", "DemoPass1234!");
            Account checking = accountService.openAccount(demoId, "BDT", BigDecimal.ZERO);
            accountService.openAccount(demoId, "BDT", new BigDecimal("50000.00"));

            // Treasury account to seed an opening balance via a balanced transaction.
            Account treasury = accountService.openAccount(adminId, "BDT", new BigDecimal("100000000"));
            ledgerService.postTransaction(
                    java.util.List.of(
                            PostingLine.debit(treasury.getId(), new BigDecimal("250000.00")),
                            PostingLine.credit(checking.getId(), new BigDecimal("250000.00"))),
                    "BDT", null, "seed-opening-balance");

            log.info("Seeded demo data: admin@ledger.local / AdminPass123!, demo@ledger.local / DemoPass1234!");
        };
    }
}
