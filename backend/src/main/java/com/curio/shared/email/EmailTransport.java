package com.curio.shared.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Delivers one already-rendered HTML email. Resend is the primary provider
 * (retried with backoff on 429/5xx, failed fast on other 4xx); SMTP, when
 * configured, is a last-ditch fallback that is deliberately not retried — if
 * Resend and SMTP are both down, the original Resend error is what operators
 * need to see. Knows nothing about what the email says.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailTransport {

    private static final String RESEND_ENDPOINT = "https://api.resend.com/emails";
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 2000;

    private final ObjectMapper objectMapper;
    private final RestTemplateBuilder restTemplateBuilder;

    /** Optional SMTP fallback. Wired only when spring.mail.* is configured. */
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from-email:no-reply@curio-news.dev}")
    private String fromEmail;

    private RestTemplate restTemplate;

    @PostConstruct
    void init() {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * @return the provider's message id (Resend's, or a unique {@code smtp-fallback:}
     *         id) for webhook correlation
     * @throws RuntimeException when neither Resend nor the SMTP fallback delivered
     */
    public String send(String to, String subject, String html) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + resendApiKey);

        Map<String, Object> body = Map.of(
                "from", "Curio <" + fromEmail + ">",
                "to", List.of(to),
                "subject", subject,
                "html", html
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        Exception lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = RETRY_DELAY_MS * attempt;
                    log.info("Retrying Resend API call (attempt {}/{}) after {}ms", attempt + 1, MAX_RETRIES + 1, delay);
                    Thread.sleep(delay);
                }
                ResponseEntity<String> response = restTemplate.exchange(RESEND_ENDPOINT, HttpMethod.POST, entity, String.class);
                return extractProviderMessageId(response.getBody());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Email send retry interrupted", ie);
            } catch (HttpClientErrorException e) {
                // 4xx other than 429 will never succeed on retry (422 bad recipient,
                // 403 unverified domain, 400 malformed body): fail straight to the SMTP
                // fallback. 429 (rate limited) falls through to the backoff loop.
                lastException = e;
                if (e.getStatusCode().value() != 429) {
                    log.warn("Resend API call for {} failed with non-retryable {} — not retrying", to, e.getStatusCode());
                    break;
                }
                log.warn("Resend API call attempt {} rate-limited (429) for {}: {}", attempt + 1, to, e.getMessage());
            } catch (Exception e) {
                lastException = e;
                log.warn("Resend API call attempt {} failed for {}: {}", attempt + 1, to, e.getMessage());
            }
        }

        log.error("Resend API call failed after {} attempts for {}", MAX_RETRIES + 1, to, lastException);

        String fallbackId = sendViaSmtpFallback(to, subject, html);
        if (fallbackId != null) {
            return fallbackId;
        }
        throw new RuntimeException("Failed to send email after retries", lastException);
    }

    private String sendViaSmtpFallback(String to, String subject, String html) {
        if (mailSender == null) {
            return null;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.warn("Email delivered via SMTP fallback for {} (Resend was unavailable)", to);
            // email_provider_id is UNIQUE, so the id must differ per send; the prefix
            // keeps it distinguishable from real Resend ids in webhook lookups.
            return "smtp-fallback:" + UUID.randomUUID();
        } catch (Exception fallbackEx) {
            log.error("SMTP fallback also failed for {}", to, fallbackEx);
            return null;
        }
    }

    private String extractProviderMessageId(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("id").asText(null);
        } catch (Exception e) {
            log.warn("Failed to parse Resend response body: {}", responseBody, e);
            return null;
        }
    }
}
