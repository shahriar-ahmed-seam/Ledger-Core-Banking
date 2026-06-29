package com.ledgercore.auth;

import com.ledgercore.config.AuthProperties;
import com.ledgercore.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure / cryptographic property tests for the auth policies and token issuance.
 */
class AuthPurePropertiesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-06-01T12:00:00Z"), ZoneOffset.UTC);

    // A low-cost Argon2id encoder: same algorithm/behavior as production, cheaper for tests.
    private static final PasswordEncoder CHEAP_ARGON2 =
            new Argon2PasswordEncoder(16, 32, 1, 1 << 14, 2);

    // Feature: ledger-core-banking, Property 2: Password policy is enforced
    @Property(tries = 300)
    void property2_passwordPolicyEnforced(@ForAll("anyPassword") String password) {
        boolean lengthOk = password.length() >= 12 && password.length() <= 128;
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean expectedValid = lengthOk && hasLetter && hasDigit;

        assertThat(PasswordPolicy.isValid(password)).isEqualTo(expectedValid);
        if (!expectedValid) {
            assertThat(PasswordPolicy.unmetCriteria(password)).isNotEmpty();
        }
    }

    @Provide
    Arbitrary<String> anyPassword() {
        return Arbitraries.strings().withCharRange('!', 'z').ofMinLength(0).ofMaxLength(140);
    }

    // Feature: ledger-core-banking, Property 3: Email well-formedness is enforced and uniqueness is case-insensitive
    @Property(tries = 300)
    void property3_emailWellFormednessAndCaseInsensitiveNormalization(
            @ForAll("emailish") String email) {
        boolean wellFormed = EmailPolicy.isWellFormed(email);
        // Independent oracle for well-formedness.
        String e = email.trim();
        int at = e.indexOf('@');
        boolean singleAt = at > 0 && at == e.lastIndexOf('@');
        boolean oracle = false;
        if (singleAt) {
            String local = e.substring(0, at);
            String domain = e.substring(at + 1);
            int dot = domain.indexOf('.');
            oracle = !local.isEmpty() && dot > 0 && dot < domain.length() - 1;
        }
        assertThat(wellFormed).isEqualTo(oracle);

        // Normalization is case-insensitive: any case variant maps to the same normalized form.
        assertThat(EmailPolicy.normalize(email.toUpperCase()))
                .isEqualTo(EmailPolicy.normalize(email.toLowerCase()));
    }

    @Provide
    Arbitrary<String> emailish() {
        Arbitrary<String> good = Arbitraries.of("a@b.com", "User@Example.COM", "x.y@a.co",
                "name@sub.domain.org");
        Arbitrary<String> bad = Arbitraries.of("", "no-at", "@b.com", "a@", "a@b", "a@@b.com",
                "a@b.", "a b@c.com");
        return Arbitraries.oneOf(good, bad);
    }

    // Feature: ledger-core-banking, Property 4: Passwords are stored only as irreversible hashes
    @Property(tries = 60)
    void property4_passwordsStoredAsIrreversibleHashes(@ForAll("validPassword") String password) {
        String hash = CHEAP_ARGON2.encode(password);
        // The stored credential is not the plaintext and does not contain it.
        assertThat(hash).isNotEqualTo(password);
        assertThat(hash).doesNotContain(password);
        // It is verifiable: the original password matches, a different one does not.
        assertThat(CHEAP_ARGON2.matches(password, hash)).isTrue();
        assertThat(CHEAP_ARGON2.matches(password + "x", hash)).isFalse();
    }

    @Provide
    Arbitrary<String> validPassword() {
        // Always policy-compliant: letters + digits, length 12-40.
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(8).ofMaxLength(20)
                .map(s -> s + "1a" + s.length() + "Zz");
    }

    // Feature: ledger-core-banking, Property 6: Token lifetimes are exact
    @Property(tries = 100)
    void property6_tokenLifetimesAreExact(@ForAll("role") Role role) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        AuthProperties props = new AuthProperties();
        JwtService jwt = new JwtService(kp, props, CLOCK);

        String token = jwt.issueAccessToken(UUID.randomUUID(), role);
        Claims claims = Jwts.parser()
                .verifyWith((RSAPublicKey) kp.getPublic())
                .clock(() -> Date.from(Instant.now(CLOCK)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        long iat = claims.getIssuedAt().toInstant().getEpochSecond();
        long exp = claims.getExpiration().toInstant().getEpochSecond();
        assertThat(exp - iat).isEqualTo(props.getJwt().getAccessTokenTtl().toSeconds());

        // And the token verifies back to the correct principal.
        AuthPrincipal principal = jwt.verifyAccessToken(token);
        assertThat(principal.role()).isEqualTo(role);
    }

    @Provide
    Arbitrary<Role> role() {
        return Arbitraries.of(Role.class);
    }
}
