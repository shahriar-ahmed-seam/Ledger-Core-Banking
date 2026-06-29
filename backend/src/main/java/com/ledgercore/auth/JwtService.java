package com.ledgercore.auth;

import com.ledgercore.common.error.DomainException;
import com.ledgercore.config.AuthProperties;
import com.ledgercore.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and verifies RS256 access-token JWTs (Requirements 2.1, 2.3, 2.5).
 */
@Service
public class JwtService {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final AuthProperties props;
    private final Clock clock;

    public JwtService(KeyPair jwtSigningKeyPair, AuthProperties props, Clock clock) {
        this.privateKey = (RSAPrivateKey) jwtSigningKeyPair.getPrivate();
        this.publicKey = (RSAPublicKey) jwtSigningKeyPair.getPublic();
        this.props = props;
        this.clock = clock;
    }

    /**
     * Issues an access token expiring exactly {@code accessTokenTtl} after issuance
     * (Requirement 2.3).
     */
    public String issueAccessToken(UUID userId, Role role) {
        Instant now = Instant.now(clock);
        Instant expiry = now.plus(props.getJwt().getAccessTokenTtl());
        return Jwts.builder()
                .issuer(props.getJwt().getIssuer())
                .subject(userId.toString())
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Verifies an access token's signature, issuer, and expiry and returns the principal.
     *
     * @throws DomainException (AUTHENTICATION_ERROR) if the token is expired, malformed, or
     *                         fails verification (Requirement 2.5).
     */
    public AuthPrincipal verifyAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(props.getJwt().getIssuer())
                    .clock(() -> Date.from(Instant.now(clock)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UUID userId = UUID.fromString(claims.getSubject());
            Role role = Role.valueOf(claims.get("role", String.class));
            return new AuthPrincipal(userId, role);
        } catch (JwtException | IllegalArgumentException e) {
            throw DomainException.authentication("Invalid or expired access token.");
        }
    }
}
