package com.ledgercore.api;

import com.ledgercore.api.dto.AuthDtos;
import com.ledgercore.auth.AuthService;
import com.ledgercore.auth.TokenPair;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Authentication endpoints (Requirements 1, 2).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Envelopes.Success<AuthDtos.RegisterResponse> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request) {
        UUID userId = authService.register(request.email(), request.password());
        return Envelopes.ok(new AuthDtos.RegisterResponse(userId, "CUSTOMER"));
    }

    @PostMapping("/login")
    public Envelopes.Success<AuthDtos.LoginResponse> login(
            @Valid @RequestBody AuthDtos.LoginRequest request) {
        TokenPair tokens = authService.login(request.email(), request.password());
        return Envelopes.ok(new AuthDtos.LoginResponse(tokens.accessToken(), tokens.refreshToken(),
                tokens.userId(), tokens.role().name()));
    }

    @PostMapping("/refresh")
    public Envelopes.Success<AuthDtos.RefreshResponse> refresh(
            @Valid @RequestBody AuthDtos.RefreshRequest request) {
        return Envelopes.ok(new AuthDtos.RefreshResponse(authService.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    public Envelopes.Success<String> logout(@Valid @RequestBody AuthDtos.LogoutRequest request) {
        authService.logout(request.refreshToken());
        return Envelopes.ok("logged_out");
    }
}
