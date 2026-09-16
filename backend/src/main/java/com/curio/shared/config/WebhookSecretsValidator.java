package com.curio.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;

@Configuration
@Profile("prod")
@Slf4j
public class WebhookSecretsValidator {

    @Value("${resend.webhook-secret:}")
    private String resendWebhookSecret;

    @Value("${app.unsubscribe-secret:}")
    private String unsubscribeSecret;

    @PostConstruct
    void assertSecretsPresent() {
        requireNonBlank(resendWebhookSecret, "RESEND_WEBHOOK_SECRET");
        requireNonBlank(unsubscribeSecret, "UNSUBSCRIBE_SECRET");
        log.info("Webhook secrets validated for prod profile");
    }

    private void requireNonBlank(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                envName + " must be set in prod profile; refusing to start with missing secret.");
        }
    }
}
