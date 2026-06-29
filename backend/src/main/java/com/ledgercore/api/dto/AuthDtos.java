package com.ledgercore.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Request/response DTOs for the authentication endpoints (Requirements 1, 2).
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record RegisterResponse(UUID userId, String role) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record LoginResponse(String accessToken, String refreshToken, UUID userId, String role) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record RefreshResponse(String accessToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }
}
