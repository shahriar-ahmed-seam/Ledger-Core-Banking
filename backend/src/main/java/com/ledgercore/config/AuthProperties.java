package com.ledgercore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Externalized authentication/security configuration (Requirements 2.3, 2.7, 2.9).
 */
@ConfigurationProperties(prefix = "ledger.security")
public class AuthProperties {

    private final Jwt jwt = new Jwt();
    private final Login login = new Login();

    public Jwt getJwt() {
        return jwt;
    }

    public Login getLogin() {
        return login;
    }

    public static class Jwt {
        /** Access token lifetime (Requirement 2.3: 15 minutes). */
        private Duration accessTokenTtl = Duration.ofMinutes(15);
        /** Refresh token lifetime (Requirement 2.7: 7 days). */
        private Duration refreshTokenTtl = Duration.ofDays(7);
        private String issuer = "ledger-core-banking";

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }
    }

    public static class Login {
        /** Failed attempts before lockout (Requirement 2.9: 5). */
        private int maxFailedAttempts = 5;
        /** Sliding window over which failures are counted (Requirement 2.9: 15 minutes). */
        private Duration lockoutWindow = Duration.ofMinutes(15);
        /** Lockout duration once tripped (Requirement 2.9: 15 minutes). */
        private Duration lockoutDuration = Duration.ofMinutes(15);

        public int getMaxFailedAttempts() {
            return maxFailedAttempts;
        }

        public void setMaxFailedAttempts(int maxFailedAttempts) {
            this.maxFailedAttempts = maxFailedAttempts;
        }

        public Duration getLockoutWindow() {
            return lockoutWindow;
        }

        public void setLockoutWindow(Duration lockoutWindow) {
            this.lockoutWindow = lockoutWindow;
        }

        public Duration getLockoutDuration() {
            return lockoutDuration;
        }

        public void setLockoutDuration(Duration lockoutDuration) {
            this.lockoutDuration = lockoutDuration;
        }
    }
}
