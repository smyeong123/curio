package com.curio.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtSecretsValidatorTest {

    private static final String STRONG_A = "a".repeat(32) + "-access-signing-key";
    private static final String STRONG_B = "b".repeat(32) + "-refresh-signing-key";

    private JwtSecretsValidator validator(String secret, String refreshSecret) {
        JwtSecretsValidator validator = new JwtSecretsValidator();
        ReflectionTestUtils.setField(validator, "secret", secret);
        ReflectionTestUtils.setField(validator, "refreshSecret", refreshSecret);
        return validator;
    }

    @Test
    void acceptsStrongDistinctSecrets() {
        assertDoesNotThrow(() -> validator(STRONG_A, STRONG_B).assertSecretsStrong());
    }

    @Test
    void rejectsMissingSecret() {
        assertThrows(IllegalStateException.class, () -> validator("", STRONG_B).assertSecretsStrong());
        assertThrows(IllegalStateException.class, () -> validator(STRONG_A, " ").assertSecretsStrong());
    }

    @Test
    void rejectsSecretShorterThan32Bytes() {
        assertThrows(IllegalStateException.class,
                () -> validator("short-key", STRONG_B).assertSecretsStrong());
    }

    @Test
    void rejectsKnownDevPlaceholders() {
        assertThrows(IllegalStateException.class,
                () -> validator("dev-only-insecure-jwt-secret-change-me-do-not-use-in-prod", STRONG_B)
                        .assertSecretsStrong());
        assertThrows(IllegalStateException.class,
                () -> validator(STRONG_A, "dev-only-insecure-jwt-refresh-secret-change-me-not-for-prod")
                        .assertSecretsStrong());
    }

    @Test
    void rejectsSharedSigningKey() {
        // Separate keys are what stop refresh tokens being replayed as access tokens.
        assertThrows(IllegalStateException.class,
                () -> validator(STRONG_A, STRONG_A).assertSecretsStrong());
    }
}
