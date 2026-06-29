package com.ledgercore.api;

import com.ledgercore.account.AccountService;
import com.ledgercore.api.dto.TransferDtos;
import com.ledgercore.auth.AuthPrincipal;
import com.ledgercore.authz.AccessAction;
import com.ledgercore.authz.AuthzService;
import com.ledgercore.transfer.TransferCommand;
import com.ledgercore.transfer.TransferResult;
import com.ledgercore.transfer.TransferService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Money-movement endpoint (Requirements 6, 7, 8).
 */
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;
    private final AccountService accountService;
    private final AuthzService authzService;

    public TransferController(TransferService transferService, AccountService accountService,
                             AuthzService authzService) {
        this.transferService = transferService;
        this.accountService = accountService;
        this.authzService = authzService;
    }

    @PostMapping
    public Envelopes.Success<TransferDtos.TransferResponse> transfer(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferDtos.TransferRequest request) {

        // Authorize: a CUSTOMER may only transfer from an account they own (Requirement 3).
        boolean ownsSource = accountService.viewAccount(request.sourceId())
                .ownerId().equals(principal.userId());
        authzService.authorize(principal, AccessAction.POST_TRANSFER, ownsSource);

        TransferResult result = transferService.transfer(new TransferCommand(
                principal.userId(), idempotencyKey, request.sourceId(), request.destinationId(),
                request.amount(), request.currency()));
        return Envelopes.ok(new TransferDtos.TransferResponse(result.transactionId(), result.replayed()));
    }
}
