package com.ledgercore.auth;

import com.ledgercore.audit.AuditService;
import com.ledgercore.common.error.DomainException;
import com.ledgercore.common.error.ErrorCode;
import com.ledgercore.config.AuthProperties;
import com.ledgercore.user.Role;
import com.ledgercore.user.User;
import com.ledgercore.user.UserRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for Auth_Service behavior using mocked collaborators.
 */
class AuthServicePropertiesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-06-01T12:00:00Z"), ZoneOffset.UTC);

    private record Mocks(AuthService service, UserRepository users, RefreshTokenRepository tokens,
                         LoginAttemptRepository attempts, JwtService jwt) {
    }

    private Mocks build() {
        UserRepository users = mock(UserRepository.class);
        RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
        LoginAttemptRepository attempts = mock(LoginAttemptRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JwtService jwt = mock(JwtService.class);
        AuditService audit = mock(AuditService.class);
        when(encoder.encode(anyString())).thenReturn("hash");
        when(jwt.issueAccessToken(any(), any())).thenReturn("access-token");
        AuthService service = new AuthService(users, tokens, attempts, encoder, jwt, audit,
                new AuthProperties(), CLOCK);
        return new Mocks(service, users, tokens, attempts, jwt);
    }

    // Feature: ledger-core-banking, Property 1: Valid registration creates exactly one CUSTOMER
    @Property(tries = 100)
    void property1_validRegistrationCreatesExactlyOneCustomer(@ForAll("localPart") String local,
                                                              @ForAll("validPassword") String password) {
        Mocks m = build();
        when(m.users().existsByEmailNormalized(anyString())).thenReturn(false);
        when(m.users().save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String email = local + "@example.com";
        UUID id = m.service().register(email, password);
        assertThat(id).isNotNull();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(m.users()).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getEmailNormalized()).isEqualTo(email.toLowerCase());
    }

    // Feature: ledger-core-banking, Property 5: Authentication is enumeration-resistant
    @Property(tries = 100)
    void property5_authenticationEnumerationResistant(@ForAll("localPart") String local,
                                                      @ForAll("validPassword") String password) {
        // Case A: unknown email.
        Mocks unknown = build();
        when(unknown.attempts().countRecentFailures(anyString(), any())).thenReturn(0L);
        when(unknown.users().findByEmailNormalized(anyString())).thenReturn(Optional.empty());
        PasswordEncoder enc = encoderOf(unknown);
        when(enc.matches(anyString(), anyString())).thenReturn(false);

        DomainException eUnknown = catchThrowableOfType(
                () -> unknown.service().login(local + "@example.com", password), DomainException.class);

        // Case B: known email, wrong password.
        Mocks known = build();
        when(known.attempts().countRecentFailures(anyString(), any())).thenReturn(0L);
        User user = new User(UUID.randomUUID(), local + "@example.com", "stored-hash",
                Role.CUSTOMER, Instant.now(CLOCK));
        when(known.users().findByEmailNormalized(anyString())).thenReturn(Optional.of(user));
        PasswordEncoder enc2 = encoderOf(known);
        when(enc2.matches(anyString(), anyString())).thenReturn(false);

        DomainException eWrong = catchThrowableOfType(
                () -> known.service().login(local + "@example.com", password), DomainException.class);

        // Both rejections are identical in code and message (no enumeration signal).
        assertThat(eUnknown).isNotNull();
        assertThat(eWrong).isNotNull();
        assertThat(eUnknown.code()).isEqualTo(eWrong.code()).isEqualTo(ErrorCode.AUTHENTICATION_ERROR);
        assertThat(eUnknown.getMessage()).isEqualTo(eWrong.getMessage());
    }

    // Feature: ledger-core-banking, Property 7: Refresh-token validity round-trip
    @Property(tries = 200)
    void property7_refreshTokenValidityRoundTrip(@ForAll boolean revoked,
                                                 @ForAll boolean expired) {
        Mocks m = build();
        UUID userId = UUID.randomUUID();
        Instant expiry = expired ? Instant.now(CLOCK).minusSeconds(10) : Instant.now(CLOCK).plusSeconds(3600);
        RefreshToken token = new RefreshToken(UUID.randomUUID(), userId, "thehash", revoked, expiry,
                Instant.now(CLOCK));
        when(m.tokens().findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(m.users().findById(userId)).thenReturn(Optional.of(
                new User(userId, "u@example.com", "h", Role.CUSTOMER, Instant.now(CLOCK))));

        boolean shouldSucceed = !revoked && !expired;
        if (shouldSucceed) {
            assertThat(m.service().refresh("raw-token")).isEqualTo("access-token");
        } else {
            assertThatThrownBy(() -> m.service().refresh("raw-token"))
                    .isInstanceOf(DomainException.class)
                    .satisfies(e -> assertThat(((DomainException) e).code())
                            .isEqualTo(ErrorCode.AUTHENTICATION_ERROR));
            verify(m.jwt(), never()).issueAccessToken(any(), any());
        }
    }

    // Feature: ledger-core-banking, Property 8: Login lockout after repeated failures
    @Property(tries = 200)
    void property8_loginLockoutAfterRepeatedFailures(@ForAll @IntRange(min = 0, max = 10) int recentFailures) {
        Mocks m = build();
        when(m.attempts().countRecentFailures(anyString(), any())).thenReturn((long) recentFailures);
        // Even if credentials would be valid, lockout must take precedence at/after the threshold.
        User user = new User(UUID.randomUUID(), "u@example.com", "h", Role.CUSTOMER, Instant.now(CLOCK));
        when(m.users().findByEmailNormalized(anyString())).thenReturn(Optional.of(user));
        PasswordEncoder enc = encoderOf(m);
        when(enc.matches(anyString(), anyString())).thenReturn(true);

        boolean lockedOut = recentFailures >= new AuthProperties().getLogin().getMaxFailedAttempts();
        if (lockedOut) {
            assertThatThrownBy(() -> m.service().login("u@example.com", "whatever"))
                    .isInstanceOf(DomainException.class)
                    .satisfies(e -> assertThat(((DomainException) e).code())
                            .isEqualTo(ErrorCode.AUTHENTICATION_ERROR));
            verify(m.jwt(), never()).issueAccessToken(any(), any());
        } else {
            assertThat(m.service().login("u@example.com", "whatever").accessToken())
                    .isEqualTo("access-token");
        }
    }

    private PasswordEncoder encoderOf(Mocks m) {
        return (PasswordEncoder) org.springframework.test.util.ReflectionTestUtils.getField(
                m.service(), "passwordEncoder");
    }

    @Provide
    Arbitrary<String> localPart() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(10);
    }

    @Provide
    Arbitrary<String> validPassword() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(10).ofMaxLength(20)
                .map(s -> s + "1a" + s.length());
    }
}
