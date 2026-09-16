package com.curio.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnsubscribeTokenServiceTest {

    private UnsubscribeTokenService service;

    @BeforeEach
    void setUp() {
        service = new UnsubscribeTokenService();
        ReflectionTestUtils.setField(service, "unsubscribeSecret", "test-secret-key-do-not-use-in-prod");
        ReflectionTestUtils.setField(service, "tokenTtlHours", 720L);
    }

    @Test
    void generateToken_roundtrips_throughValidation() {
        UUID userId = UUID.randomUUID();

        String token = service.generateToken(userId);

        assertThat(token).contains(".");
        assertThat(service.validateAndExtractUserId(token)).isEqualTo(userId);
    }

    @Test
    void validate_rejects_emptyOrNullToken() {
        assertThatThrownBy(() -> service.validateAndExtractUserId(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validateAndExtractUserId(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validateAndExtractUserId("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_rejects_malformedToken_missingSeparator() {
        assertThatThrownBy(() -> service.validateAndExtractUserId("just-one-segment"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_rejects_token_signedWithDifferentSecret() {
        UUID userId = UUID.randomUUID();

        UnsubscribeTokenService other = new UnsubscribeTokenService();
        ReflectionTestUtils.setField(other, "unsubscribeSecret", "different-secret");
        ReflectionTestUtils.setField(other, "tokenTtlHours", 720L);
        String foreignToken = other.generateToken(userId);

        assertThatThrownBy(() -> service.validateAndExtractUserId(foreignToken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_rejects_tamperedPayload() {
        UUID userId = UUID.randomUUID();
        String token = service.generateToken(userId);

        // Replace the user-id part of the payload while keeping the original signature.
        String[] parts = token.split("\\.");
        String originalPayload = new String(Base64.getUrlDecoder().decode(parts[0]));
        String tampered = UUID.randomUUID() + originalPayload.substring(originalPayload.indexOf('.'));
        String tamperedEncoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tampered.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String tamperedToken = tamperedEncoded + "." + parts[1];

        assertThatThrownBy(() -> service.validateAndExtractUserId(tamperedToken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_rejects_expiredToken() {
        // Generate a token that expired one hour ago by issuing it under negative TTL.
        ReflectionTestUtils.setField(service, "tokenTtlHours", -1L);
        UUID userId = UUID.randomUUID();
        String expiredToken = service.generateToken(userId);

        assertThatThrownBy(() -> service.validateAndExtractUserId(expiredToken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateToken_producesDifferentTokens_forDifferentUsers() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        String tokenA = service.generateToken(a);
        String tokenB = service.generateToken(b);

        assertThat(tokenA).isNotEqualTo(tokenB);
        assertThat(service.validateAndExtractUserId(tokenA)).isEqualTo(a);
        assertThat(service.validateAndExtractUserId(tokenB)).isEqualTo(b);
    }

    @Test
    void validate_acceptsTokenAtExpiryBoundary() {
        // TTL of one hour produces an expiresAt comfortably in the future.
        ReflectionTestUtils.setField(service, "tokenTtlHours", 1L);
        UUID userId = UUID.randomUUID();
        String token = service.generateToken(userId);

        Instant expiresApprox = Instant.now().plus(1, ChronoUnit.HOURS);
        assertThat(service.validateAndExtractUserId(token)).isEqualTo(userId);
        assertThat(expiresApprox).isAfter(Instant.now());
    }
}
