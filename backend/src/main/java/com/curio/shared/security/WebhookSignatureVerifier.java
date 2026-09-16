package com.curio.shared.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Component
@Slf4j
public class WebhookSignatureVerifier {

    @Value("${resend.webhook-secret:}")
    private String webhookSecret;

    @Value("${resend.webhook-tolerance-seconds:300}")
    private long toleranceSeconds;

    public boolean verify(String payload, HttpHeaders headers) {
        String messageId = firstHeader(headers, "svix-id", "webhook-id");
        String timestamp = firstHeader(headers, "svix-timestamp", "webhook-timestamp");
        String signatureHeader = firstHeader(headers, "svix-signature", "webhook-signature");

        if (isBlank(messageId) || isBlank(timestamp) || isBlank(signatureHeader)) {
            log.warn("Webhook signature headers missing");
            return false;
        }

        if (isBlank(webhookSecret)) {
            log.error("Webhook secret is not configured");
            return false;
        }

        long eventTimestamp;
        try {
            eventTimestamp = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            log.warn("Webhook timestamp is invalid: {}", timestamp);
            return false;
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - eventTimestamp) > toleranceSeconds) {
            log.warn("Webhook timestamp outside tolerance window");
            return false;
        }

        String signedPayload = messageId + "." + timestamp + "." + payload;
        byte[] expectedSignature;
        try {
            expectedSignature = sign(signedPayload, webhookSecret);
        } catch (Exception ex) {
            log.error("Failed to compute webhook signature", ex);
            return false;
        }

        for (String signatureEntry : signatureHeader.split(" ")) {
            String[] parts = signatureEntry.split(",", 2);
            if (parts.length != 2) {
                continue;
            }
            if (!"v1".equals(parts[0])) {
                continue;
            }
            if (constantTimeBase64Equals(expectedSignature, parts[1])) {
                return true;
            }
        }

        log.warn("Webhook signature verification failed");
        return false;
    }

    private String firstHeader(HttpHeaders headers, String... names) {
        for (String name : names) {
            String value = headers.getFirst(name);
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private byte[] sign(String payload, String secret) throws Exception {
        String normalizedSecret = secret.startsWith("whsec_") ? secret.substring(6) : secret;
        byte[] key = Base64.getDecoder().decode(normalizedSecret);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    }

    private boolean constantTimeBase64Equals(byte[] expected, String providedBase64) {
        byte[] provided;
        try {
            provided = Base64.getDecoder().decode(providedBase64);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return MessageDigest.isEqual(expected, provided);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
