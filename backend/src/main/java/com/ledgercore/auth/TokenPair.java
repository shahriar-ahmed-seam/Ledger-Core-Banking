package com.ledgercore.auth;

import com.ledgercore.user.Role;

import java.util.UUID;

/**
 * The tokens issued on successful authentication (Requirement 2.1).
 */
public record TokenPair(String accessToken, String refreshToken, UUID userId, Role role) {
}
