package com.curio.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyCipherTest {

    // A real (non-zero) 32-byte key — the round-trip tests just need a valid key,
    // not the all-zero dev placeholder (which is now rejected outside dev/test).
    private static final String TEST_KEY =
            Base64.getEncoder().encodeToString("curio-test-key-0123456789abcdef!".getBytes());

    private ApiKeyCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = new ApiKeyCipher(TEST_KEY, new MockEnvironment());
        cipher.init();
    }

    @Test
    void roundTrips_smallSecret() {
        String secret = "sk-ant-api03-roundtrip-token-abcdef1234567890";
        ApiKeyCipher.Sealed sealed = cipher.encrypt(secret);

        assertThat(sealed.iv()).hasSize(12);
        assertThat(new String(sealed.ciphertext())).doesNotContain(secret);   // not plaintext
        assertThat(cipher.decrypt(sealed.ciphertext(), sealed.iv())).isEqualTo(secret);
    }

    @Test
    void roundTrips_unicodeSecret() {
        String secret = "키-한글-secret-😀-🔑-edge-case-1234";
        ApiKeyCipher.Sealed sealed = cipher.encrypt(secret);
        assertThat(cipher.decrypt(sealed.ciphertext(), sealed.iv())).isEqualTo(secret);
    }

    @Test
    void differentInvocations_produceDifferentCiphertexts() {
        // GCM with random IV — same plaintext must encrypt to different bytes each time.
        ApiKeyCipher.Sealed a = cipher.encrypt("same-input");
        ApiKeyCipher.Sealed b = cipher.encrypt("same-input");
        assertThat(a.iv()).isNotEqualTo(b.iv());
        assertThat(a.ciphertext()).isNotEqualTo(b.ciphertext());
    }

    @Test
    void decryptingTamperedCiphertext_fails() {
        ApiKeyCipher.Sealed sealed = cipher.encrypt("untampered");
        byte[] tampered = sealed.ciphertext().clone();
        tampered[0] ^= 1;   // flip a single bit
        assertThatThrownBy(() -> cipher.decrypt(tampered, sealed.iv()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decryptingWithWrongIv_fails() {
        ApiKeyCipher.Sealed sealed = cipher.encrypt("untampered");
        byte[] wrongIv = new byte[12];   // all zeros
        assertThatThrownBy(() -> cipher.decrypt(sealed.ciphertext(), wrongIv))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decryptingWithWrongKey_fails() {
        // Encrypt with the dev key, then try to decrypt with a different key.
        ApiKeyCipher.Sealed sealed = cipher.encrypt("only-the-original-key-can-read-me");
        ApiKeyCipher otherCipher = new ApiKeyCipher(
                Base64.getEncoder().encodeToString("a-different-32-byte-key-1234567x".getBytes()),
                new MockEnvironment());
        otherCipher.init();
        assertThatThrownBy(() -> otherCipher.decrypt(sealed.ciphertext(), sealed.iv()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidKeyLength_failsAtInit() {
        ApiKeyCipher bad = new ApiKeyCipher(
                Base64.getEncoder().encodeToString(new byte[16]),  // 128-bit, not 256
                new MockEnvironment());
        assertThatThrownBy(bad::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 raw bytes");
    }

    @Test
    void invalidBase64_failsAtInit() {
        ApiKeyCipher bad = new ApiKeyCipher("not-base64!!!", new MockEnvironment());
        assertThatThrownBy(bad::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void prodProfile_requiresExplicitKey() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ApiKeyCipher cipher = new ApiKeyCipher("", env);
        assertThatThrownBy(cipher::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API_KEY_ENCRYPTION_KEY is required in prod");
    }

    @Test
    void devProfile_falls_back_when_key_absent() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        ApiKeyCipher cipher = new ApiKeyCipher("", env);
        cipher.init();   // must not throw
        ApiKeyCipher.Sealed sealed = cipher.encrypt("works");
        assertThat(cipher.decrypt(sealed.ciphertext(), sealed.iv())).isEqualTo("works");
    }

    @Test
    void unknownProfile_requiresExplicitKey() {
        // A staging/custom profile (or a missing one) must NOT silently fall back
        // to the dev key — that is the misconfigured-prod hazard we guard against.
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("staging");
        ApiKeyCipher cipher = new ApiKeyCipher("", env);
        assertThatThrownBy(cipher::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API_KEY_ENCRYPTION_KEY is required");
    }

    @Test
    void noProfile_requiresExplicitKey() {
        MockEnvironment env = new MockEnvironment();   // no profile at all
        ApiKeyCipher cipher = new ApiKeyCipher("", env);
        assertThatThrownBy(cipher::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API_KEY_ENCRYPTION_KEY is required");
    }

    @Test
    void allZeroKey_rejectedOutsideDevTest() {
        // Supplying the publicly-known all-zero dev placeholder as a *real* key
        // (correct length, but a constant anyone can reproduce) must fail fast
        // in prod/staging rather than silently encrypt every key with it.
        String allZero = Base64.getEncoder().encodeToString(new byte[32]);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ApiKeyCipher cipher = new ApiKeyCipher(allZero, env);
        assertThatThrownBy(cipher::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("all-zero dev placeholder");
    }
}
