package com.ledgercore.api;

import com.ledgercore.account.AccountService;
import com.ledgercore.account.AccountView;
import com.ledgercore.api.dto.AccountDtos;
import com.ledgercore.auth.AuthPrincipal;
import com.ledgercore.authz.AccessAction;
import com.ledgercore.authz.AuthzService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Account lifecycle and balance endpoints (Requirement 4), with RBAC ownership checks
 * (Requirement 3).
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;
    private final AuthzService authzService;

    public AccountController(AccountService accountService, AuthzService authzService) {
        this.accountService = accountService;
        this.authzService = authzService;
    }

    @PostMapping
    public Envelopes.Success<AccountDtos.AccountResponse> open(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody AccountDtos.OpenAccountRequest request) {
        BigDecimal overdraft = request.overdraftLimit() == null ? BigDecimal.ZERO
                : new BigDecimal(request.overdraftLimit());
        var account = accountService.openAccount(principal.userId(), request.currency(), overdraft);
        return Envelopes.ok(AccountDtos.AccountResponse.from(accountService.viewAccount(account.getId())));
    }

    @GetMapping
    public Envelopes.Success<List<AccountDtos.AccountResponse>> listOwn(
            @AuthenticationPrincipal AuthPrincipal principal) {
        List<AccountDtos.AccountResponse> accounts = accountService.listOwnedAccounts(principal.userId())
                .stream().map(AccountDtos.AccountResponse::from).toList();
        return Envelopes.ok(accounts);
    }

    @GetMapping("/{id}")
    public Envelopes.Success<AccountDtos.AccountResponse> view(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        AccountView view = accountService.viewAccount(id);
        authzService.authorize(principal, AccessAction.VIEW_ACCOUNT,
                view.ownerId().equals(principal.userId()));
        return Envelopes.ok(AccountDtos.AccountResponse.from(view));
    }

    @PostMapping("/{id}/close")
    public Envelopes.Success<String> close(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID id) {
        AccountView view = accountService.viewAccount(id);
        authzService.authorize(principal, AccessAction.MODIFY_ACCOUNT,
                view.ownerId().equals(principal.userId()));
        accountService.closeAccount(id);
        return Envelopes.ok("closed");
    }
}
