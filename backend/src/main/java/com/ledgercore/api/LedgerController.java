package com.ledgercore.api;

import com.ledgercore.account.AccountService;
import com.ledgercore.account.AccountView;
import com.ledgercore.api.dto.LedgerDtos;
import com.ledgercore.auth.AuthPrincipal;
import com.ledgercore.authz.AccessAction;
import com.ledgercore.authz.AuthzService;
import com.ledgercore.ledger.LedgerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Ledger query and statement endpoints (Requirement 10), with RBAC (Requirements 3, 10.5).
 */
@RestController
@RequestMapping("/api/v1/accounts/{id}")
public class LedgerController {

    private final LedgerService ledgerService;
    private final AccountService accountService;
    private final AuthzService authzService;

    public LedgerController(LedgerService ledgerService, AccountService accountService,
                           AuthzService authzService) {
        this.ledgerService = ledgerService;
        this.accountService = accountService;
        this.authzService = authzService;
    }

    @GetMapping("/entries")
    public Envelopes.Success<LedgerDtos.PageResponse<LedgerDtos.EntryResponse>> entries(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        authorizeLedger(principal, id);
        return Envelopes.ok(LedgerDtos.PageResponse.ofEntries(
                ledgerService.listEntries(id, from, to, cursor, size)));
    }

    @GetMapping("/statement")
    public Envelopes.Success<LedgerDtos.PageResponse<LedgerDtos.StatementLineResponse>> statement(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        authorizeLedger(principal, id);
        return Envelopes.ok(LedgerDtos.PageResponse.ofStatement(
                ledgerService.statement(id, from, to, cursor, size)));
    }

    private void authorizeLedger(AuthPrincipal principal, UUID accountId) {
        AccountView view = accountService.viewAccount(accountId);
        authzService.authorize(principal, AccessAction.VIEW_LEDGER,
                view.ownerId().equals(principal.userId()));
    }
}
