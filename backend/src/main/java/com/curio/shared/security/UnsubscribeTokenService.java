package com.curio.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
public class UnsubscribeTokenService {

    @Value("${app.unsubscribe-secret}")
    private String unsubscribeSecret;

    @Value("${app.unsubscribe-token-ttl-hours:720}")
    private long tokenTtlHours;

    public String generateToken(UUID userId) {
        long expiresAtEpochSeconds = Instant.now()
                .plus(tokenTtlHours, ChronoUnit.HOURS)
                .getEpochSecond();

        String payload = userId + "." + expiresAtEpochSeconds;
        String payloadEncoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signatureEncoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(sign(payload));

        return payloadEncoded + "." + signatureEncoded;
    }

    public UUID validateAndExtractUserId(String token) {
        try {
            if (token == null || token.isBlank()) {
                throw invalidToken();
            }

            String[] tokenParts = token.split("\\.");
            if (tokenParts.length != 2) {
                throw invalidToken();
            }

            String payload = new String(
                    Base64.getUrlDecoder().decode(tokenParts[0]),
                    StandardCharsets.UTF_8
            );
            byte[] providedSignature = Base64.getUrlDecoder().decode(tokenParts[1]);
            byte[] expectedSignature = sign(payload);

            if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
                throw invalidToken();
            }

            int separatorIndex = payload.lastIndexOf('.');
            if (separatorIndex <= 0 || separatorIndex >= payload.length() - 1) {
                throw invalidToken();
            }

            UUID userId = UUID.fromString(payload.substring(0, separatorIndex));
            long expiresAt = Long.parseLong(payload.substring(separatorIndex + 1));

            if (Instant.now().getEpochSecond() > expiresAt) {
                throw invalidToken();
            }

            return userId;
        } catch (Exception ex) {
            throw invalidToken();
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(unsubscribeSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign unsubscribe token", ex);
        }
    }

    private IllegalArgumentException invalidToken() {
        return new IllegalArgumentException("Invalid unsubscribe link.");
    }
}
