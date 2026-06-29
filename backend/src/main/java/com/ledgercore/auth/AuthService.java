package com.ledgercore.auth;

import com.ledgercore.audit.AuditAction;
import com.ledgercore.audit.AuditService;
import com.ledgercore.common.error.DomainException;
import com.ledgercore.config.AuthProperties;
import com.ledgercore.user.Role;
import com.ledgercore.user.User;
import com.ledgercore.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Authentication, registration, token issuance, refresh, logout, and brute-force lockout
 * (Requirements 1, 2).
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final AuthProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** A precomputed hash used to equalize timing for unknown emails (Requirement 2.2). */
    private final String dummyHash;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       LoginAttemptRepository loginAttemptRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditService auditService,
                       AuthProperties props,
                       Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.props = props;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-equalization-1");
    }

    /**
     * Registers a new CUSTOMER (Requirement 1).
     *
     * @return the new user's identifier.
     */
    @Transactional
    public UUID register(String email, String rawPassword) {
        if (!EmailPolicy.isWellFormed(email)) {
            throw DomainException.validation("Email is not well-formed.", "email",
                    "must be a valid email address");                       // R1.6
        }
        List<String> unmet = PasswordPolicy.unmetCriteria(rawPassword);
        if (!unmet.isEmpty()) {
            throw DomainException.validation("Password does not meet policy.",
                    unmet.stream().map(u -> new com.ledgercore.common.error.FieldError("password", u)).toList());
        }
        String normalized = EmailPolicy.normalize(email);
        if (userRepository.existsByEmailNormalized(normalized)) {
            throw DomainException.conflict("An account with this email already exists."); // R1.2
        }
        UUID id = UUID.randomUUID();
        userRepository.save(new User(id, normalized, passwordEncoder.encode(rawPassword),
                Role.CUSTOMER, Instant.now(clock)));                        // R1.1, R1.4
        return id;
    }

    /**
     * Authenticates and issues tokens, with enumeration resistance and lockout
     * (Requirements 2.1, 2.2, 2.9).
     */
    @Transactional
    public TokenPair login(String email, String rawPassword) {
        String normalized = EmailPolicy.normalize(email);

        if (isLockedOut(normalized)) {
            auditService.recordIndependent(null, AuditAction.AUTH_LOGIN, normalized, false, "locked-out");
            throw DomainException.authentication("Authentication failed.");  // R2.9 (generic)
        }

        Optional<User> userOpt = normalized == null ? Optional.empty()
                : userRepository.findByEmailNormalized(normalized);

        // Always perform a hash comparison to avoid leaking whether the email exists (R2.2).
        boolean matches;
        User user = userOpt.orElse(null);
        if (user != null) {
            matches = passwordEncoder.matches(rawPassword, user.getPasswordHash());
        } else {
            passwordEncoder.matches(rawPassword, dummyHash);
            matches = false;
        }

        if (!matches) {
            loginAttemptRepository.save(new LoginAttempt(normalized, false, Instant.now(clock)));
            auditService.recordIndependent(user == null ? null : user.getId(),
                    AuditAction.AUTH_LOGIN, normalized, false, "bad-credentials");
            throw DomainException.authentication("Authentication failed.");  // R2.2 (generic)
        }

        loginAttemptRepository.save(new LoginAttempt(normalized, true, Instant.now(clock)));
        TokenPair tokens = issueTokens(user);
        auditService.record(user.getId(), AuditAction.AUTH_LOGIN, user.getId().toString(), true, "login");
        return tokens;
    }

    /**
     * Issues a new access token from a valid refresh token (Requirements 2.4, 2.8).
     */
    @Transactional
    public String refresh(String refreshToken) {
        RefreshToken record = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> {
                    auditService.recordIndependent(null, AuditAction.AUTH_REFRESH, null, false, "unknown-token");
                    return DomainException.authentication("Invalid refresh token.");
                });
        if (record.isRevoked() || record.getExpiresAt().isBefore(Instant.now(clock))) {
            auditService.recordIndependent(record.getUserId(), AuditAction.AUTH_REFRESH,
                    record.getUserId().toString(), false, "revoked-or-expired");
            throw DomainException.authentication("Invalid refresh token.");  // R2.8
        }
        User user = userRepository.findById(record.getUserId())
                .orElseThrow(() -> DomainException.authentication("Invalid refresh token."));
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getRole());
        auditService.record(user.getId(), AuditAction.AUTH_REFRESH, user.getId().toString(), true, "refresh");
        return accessToken;
    }

    /**
     * Invalidates a refresh token on logout (Requirement 2.6).
     */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken)).ifPresent(record -> {
            record.setRevoked(true);
            refreshTokenRepository.save(record);
            auditService.recordIndependent(record.getUserId(), AuditAction.AUTH_LOGOUT,
                    record.getUserId().toString(), true, "logout");
        });
    }

    private boolean isLockedOut(String normalizedEmail) {
        if (normalizedEmail == null) {
            return false;
        }
        Instant since = Instant.now(clock).minus(props.getLogin().getLockoutWindow());
        long failures = loginAttemptRepository.countRecentFailures(normalizedEmail, since);
        return failures >= props.getLogin().getMaxFailedAttempts();
    }

    private TokenPair issueTokens(User user) {
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getRole());
        String refreshToken = generateRefreshToken();
        UUID jti = UUID.randomUUID();
        Instant expires = Instant.now(clock).plus(props.getJwt().getRefreshTokenTtl());
        refreshTokenRepository.save(new RefreshToken(jti, user.getId(), hash(refreshToken),
                false, expires, Instant.now(clock)));
        return new TokenPair(accessToken, refreshToken, user.getId(), user.getRole());
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
