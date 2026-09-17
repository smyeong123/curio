package com.curio.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * The at-rest form of every opaque secret the auth flow persists: refresh
 * tokens, password-reset tokens, login challenge ids and one-time codes are
 * all stored as this digest, never in plaintext, so a database leak cannot
 * be replayed against the API.
 */
final class TokenHasher {

    private TokenHasher() {}

    /** SHA-256 of the raw secret, URL-safe Base64 without padding. */
    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash token", e);
        }
    }
}
