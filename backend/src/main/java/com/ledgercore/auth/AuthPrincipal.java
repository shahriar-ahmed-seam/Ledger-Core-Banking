package com.ledgercore.auth;

import com.ledgercore.user.Role;

import java.util.UUID;

/**
 * The authenticated identity carried on a request after JWT verification.
 */
public record AuthPrincipal(UUID userId, Role role) {
}
