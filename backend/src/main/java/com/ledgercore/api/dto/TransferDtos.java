package com.ledgercore.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request/response DTOs for transfers (Requirements 6, 8).
 */
public final class TransferDtos {

    private TransferDtos() {
    }

    public record TransferRequest(@NotNull UUID sourceId, @NotNull UUID destinationId,
                                  @NotBlank String amount, @NotBlank String currency) {
    }

    public record TransferResponse(UUID transactionId, boolean replayed) {
    }
}
