package com.ledgercore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

/**
 * Security primitives: the RS256 signing key pair and the Argon2id password encoder.
 *
 * <p>For local/dev runs an RSA key pair is generated at startup. In production these would
 * be loaded from a managed secret/key store.</p>
 */
@Configuration
public class SecurityKeyConfig {

    /** RSA key pair used to sign and verify access-token JWTs (RS256). */
    @Bean
    public KeyPair jwtSigningKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key generation failed", e);
        }
    }

    /**
     * Argon2id password encoder (Requirement 1.4): salted, irreversible password hashing.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
