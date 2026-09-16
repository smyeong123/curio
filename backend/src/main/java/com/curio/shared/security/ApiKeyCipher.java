package com.curio.shared.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * AES-256-GCM symmetric cipher for at-rest BYOK encryption.
 *
 * Key sourced from {@code API_KEY_ENCRYPTION_KEY} env var (Base64-encoded
 * 32 raw bytes — generate with {@code openssl rand -base64 32}).
 *
 * In dev profile a constant key is provided so contributors aren't blocked,
 * but the bean logs a loud warning. In prod profile the key is REQUIRED;
 * starting without it fails fast.
 *
 * Encrypt format returned to callers: a {@link Sealed} record holding
 * (ciphertext+tag, iv). Both are persisted; the tag is appended to the
 * ciphertext per JCA convention so {@link #decrypt} only needs both blobs.
 */
@Component
@Slf4j
public class ApiKeyCipher {

    private static final String ALGO = "AES";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;        // 96 bits — GCM standard
    private static final int TAG_LENGTH_BITS = 128; // 16 byte authentication tag

    private final String configuredKey;
    private final boolean insecureFallbackAllowed;
    private SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyCipher(@Value("${app.api-key-encryption-key:}") String configuredKey,
                        Environment env) {
        this.configuredKey = configuredKey;
        // Default-secure: the all-zero dev fallback is permitted ONLY when a
        // "dev" or "test" profile is EXPLICITLY active. Any other situation —
        // prod, a staging/custom profile, or a missing profile — REQUIRES a real
        // key, so a misconfigured deploy fails fast instead of silently
        // encrypting every key with a publicly-known constant.
        List<String> profiles = Arrays.asList(env.getActiveProfiles());
        this.insecureFallbackAllowed = profiles.contains("dev") || profiles.contains("test");
    }

    @PostConstruct
    public void init() {
        String keyMaterial = configuredKey;
        if (keyMaterial == null || keyMaterial.isBlank()) {
            if (!insecureFallbackAllowed) {
                throw new IllegalStateException(
                        "API_KEY_ENCRYPTION_KEY is required in prod (and any non dev/test " +
                        "profile) — 32 bytes Base64. Generate with: openssl rand -base64 32");
            }
            // Dev fallback — deterministic 32-byte all-zero key so contributors
            // can decrypt across restarts. NOT secure against attackers with
            // source-code access; the prod-profile guard above prevents using it
            // outside dev.
            keyMaterial = Base64.getEncoder().encodeToString(new byte[32]);
            log.warn("⚠ API_KEY_ENCRYPTION_KEY not set — using a dev-only fallback. " +
                    "DO NOT use this in production. Generate one with: openssl rand -base64 32");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(keyMaterial);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("API_KEY_ENCRYPTION_KEY must be Base64-encoded", e);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "API_KEY_ENCRYPTION_KEY must decode to 32 raw bytes (got " +
                    keyBytes.length + "). Generate with: openssl rand -base64 32");
        }
        // Reject the publicly-known all-zero dev fallback if it is ever supplied
        // as a *real* key outside dev/test (e.g. copy-pasted from a sample env).
        // Length alone would accept it, silently encrypting every stored key
        // with a constant anyone with source access can reproduce.
        if (!insecureFallbackAllowed && isAllZero(keyBytes)) {
            throw new IllegalStateException(
                    "API_KEY_ENCRYPTION_KEY is the all-zero dev placeholder — refusing to " +
                    "start outside dev/test. Generate a real one with: openssl rand -base64 32");
        }
        this.secretKey = new SecretKeySpec(keyBytes, ALGO);
    }

    public Sealed encrypt(String plaintext) {
        // IllegalState (→ generic 500), not IllegalArgument (→ 400 with message):
        // cipher inputs come from our own code/DB, so a violation is an internal
        // bug or data corruption — never something to describe to the client.
        if (plaintext == null) throw new IllegalStateException("plaintext required");
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new Sealed(ciphertext, iv);
        } catch (Exception e) {
            // Don't include any plaintext fragment in the message.
            throw new IllegalStateException("Failed to encrypt API key", e);
        }
    }

    public String decrypt(byte[] ciphertext, byte[] iv) {
        // IllegalState for the same reason as encrypt(): these inputs are ours,
        // so failures here are internal and must not surface to the client.
        if (ciphertext == null || iv == null) {
            throw new IllegalStateException("ciphertext + iv required");
        }
        if (iv.length != IV_LENGTH) {
            throw new IllegalStateException("iv must be 12 bytes (got " + iv.length + ")");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(ciphertext);
            return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // GCM throws AEADBadTagException on tamper / wrong-key; treat them
            // all as opaque to avoid leaking attack signal via error messages.
            throw new IllegalStateException("Failed to decrypt API key", e);
        }
    }

    private static boolean isAllZero(byte[] bytes) {
        int acc = 0;
        for (byte b : bytes) {
            acc |= b;
        }
        return acc == 0;
    }

    public record Sealed(byte[] ciphertext, byte[] iv) { }
}
