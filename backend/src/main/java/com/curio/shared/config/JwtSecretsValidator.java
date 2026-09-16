package com.curio.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Fail-fast prod guard for the JWT signing secrets (mirrors {@link WebhookSecretsValidator}).
 *
 * <p>Without this, a weak secret passes boot and health checks and only surfaces at
 * runtime: {@code Keys.hmacShaKeyFor} throws {@code WeakKeyException} on the FIRST
 * token issuance (every login 500s after a "successful" deploy), and a low-entropy
 * secret that happens to be ≥32 bytes silently leaves tokens forgeable. Validate at
 * boot instead so a misconfigured deploy never goes live.
 */
@Configuration
@Profile("prod")
@Slf4j
public class JwtSecretsValidator {

    /** HMAC-SHA256 requires a key of at least 256 bits (RFC 7518 §3.2). */
    private static final int MIN_SECRET_BYTES = 32;

    /** Known non-production placeholder values that must never sign prod tokens. */
    private static final Set<String> KNOWN_PLACEHOLDERS = Set.of(
            "dev-only-insecure-jwt-secret-change-me-do-not-use-in-prod",
            "dev-only-insecure-jwt-refresh-secret-change-me-not-for-prod");

    @Value("${jwt.secret:}")
    private String secret;

    @Value("${jwt.refresh-secret:}")
    private String refreshSecret;

    @PostConstruct
    void assertSecretsStrong() {
        requireStrong(secret, "JWT_SECRET");
        requireStrong(refreshSecret, "JWT_REFRESH_SECRET");
        if (secret.equals(refreshSecret)) {
            // Separate keys are what stop a refresh token from being replayed as an
            // access token (and vice versa); a shared key collapses that boundary.
            throw new IllegalStateException(
                    "JWT_SECRET and JWT_REFRESH_SECRET must differ; refusing to start with a shared signing key.");
        }
        log.info("JWT signing secrets validated for prod profile");
    }

    private void requireStrong(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    envName + " must be set in prod profile; refusing to start with missing secret.");
        }
        if (KNOWN_PLACEHOLDERS.contains(value)) {
            throw new IllegalStateException(
                    envName + " is set to a dev placeholder; refusing to start with a known public value.");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    envName + " must be at least " + MIN_SECRET_BYTES
                    + " bytes for HMAC-SHA256; refusing to start with a weak signing key.");
        }
    }
}
