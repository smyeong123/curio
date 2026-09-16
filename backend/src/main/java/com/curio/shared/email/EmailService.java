package com.curio.shared.email;

import com.curio.news.entity.Digest;
import com.curio.news.port.out.DigestPort;
import com.curio.quiz.port.out.QuizPort;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import com.curio.shared.port.in.EmailUseCase;
import com.curio.auth.service.UnsubscribeTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import jakarta.mail.internet.MimeMessage;

import jakarta.annotation.PostConstruct;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService implements EmailUseCase {

    private final DigestPort digestPort;
    private final UserPort userPort;
    private final QuizPort quizPort;
    private final TemplateEngine templateEngine;
    private final ObjectMapper objectMapper;
    private final RestTemplateBuilder restTemplateBuilder;
    private final UnsubscribeTokenService unsubscribeTokenService;

    /** Optional SMTP fallback. Wired only when spring.mail.* is configured. */
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from-email:no-reply@curio-news.dev}")
    private String fromEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl;

    private RestTemplate restTemplate;

    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 2000;

    @PostConstruct
    void init() {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    public DigestSendOutcome sendDigestEmail(User user, Digest digest) {
        // Don't swallow failures here — callers (AdminOperationsService, EmailSendJob)
        // need to know whether the send succeeded so their counters are honest.
        // Wrapping in a try/catch that logs-and-returns previously made the metrics
        // lie ("sentCount: 7" when 6 were Resend 403s).

        // Claim BEFORE sending: the conditional update (WHERE email_sent_at IS NULL)
        // commits immediately, so when the hourly EmailSendJob, an admin batch, and a
        // Studio send race for the same digest, exactly one wins — the user can never
        // receive the same digest twice. Losing the claim is a normal no-op.
        LocalDateTime claimedAt = LocalDateTime.now();
        if (digestPort.claimForEmailSend(digest.getId(), claimedAt) == 0) {
            log.info("Digest {} already sent or being sent by another path — skipping "
                    + "duplicate email to user {}", digest.getId(), user.getId());
            return DigestSendOutcome.ALREADY_CLAIMED;
        }

        String html = renderDigestEmail(user, digest);
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
        String subject = "Your Curio Daily Digest — " + date;

        String providerMessageId;
        try {
            providerMessageId = sendViaResend(user.getEmail(), subject, html);
        } catch (RuntimeException e) {
            // The provider send failed after we claimed — free the digest so a later
            // run retries it, then rethrow so caller counters stay honest.
            try {
                digestPort.releaseEmailClaim(digest.getId());
            } catch (RuntimeException release) {
                log.error("Failed to release email claim for digest {} after send failure — "
                        + "it will not be retried automatically. Manual reconciliation needed.",
                        digest.getId(), release);
            }
            throw e;
        }

        // Keep the caller's in-memory entity consistent with what the claim wrote
        // (a full save() here would overwrite the claim with stale state).
        digest.setEmailSentAt(claimedAt);
        digest.setEmailProviderId(providerMessageId);
        recordProviderId(digest, providerMessageId, user);

        log.info("Digest email sent to user {}", user.getId());
        return DigestSendOutcome.SENT;
    }

    private void recordProviderId(Digest digest, String providerMessageId, User user) {
        // email_sent_at is already committed by the claim, so a failure here can no
        // longer cause a duplicate send — only a missing webhook correlation id.
        // Log loudly but don't throw: the email genuinely went out.
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                digestPort.updateEmailProviderId(digest.getId(), providerMessageId);
                return;
            } catch (RuntimeException e) {
                log.warn("Failed to record email provider id for digest {} (attempt {}/3)",
                        digest.getId(), attempt, e);
            }
        }
        log.error("Email sent but provider id {} not recorded for user {} digest {} — "
                + "open/click webhooks for this send won't correlate.",
                providerMessageId, user.getId(), digest.getId());
    }

    public void sendPasswordResetEmail(User user, String resetToken) {
        String link = frontendUrl + "/reset-password?token=" +
                URLEncoder.encode(resetToken, StandardCharsets.UTF_8);
        String body = """
                              <div style="font-family:'Courier New', monospace; font-size:11px; letter-spacing:2px; text-transform:uppercase; color:#c4290a; margin-bottom:14px;">Password reset</div>
                              <h1 style="margin:0 0 16px 0; font-family:Georgia, 'Times New Roman', serif; font-size:28px; line-height:1.15; font-weight:600; color:#14130f;">Reset your password</h1>
                              <p style="margin:0 0 24px 0; font-size:15px; line-height:1.65; color:#2c2a23;">
                                We received a request to reset the password for your Curio account. Click the button below to choose a new one. This link expires in <strong>1 hour</strong>.
                              </p>

                              <!-- Button. A real link, so the click works in every mail client
                                   (unlike a JS copy button, which clients strip). -->
                              <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 28px 0;">
                                <tr>
                                  <td style="background-color:#14130f;">
                                    <a href="__RESET_LINK__" style="display:inline-block; padding:14px 28px; font-family:'Courier New', monospace; font-size:13px; font-weight:700; letter-spacing:1.5px; text-transform:uppercase; color:#f3ede1; text-decoration:none; border:1px solid #14130f;">Reset password &rarr;</a>
                                  </td>
                                </tr>
                              </table>

                              <p style="margin:0 0 6px 0; font-size:13px; line-height:1.6; color:#6e695b;">Button not working? Paste this link into your browser:</p>
                              <p style="margin:0 0 28px 0; font-size:12px; line-height:1.5; word-break:break-all;">
                                <a href="__RESET_LINK__" style="color:#c4290a; text-decoration:underline;">__RESET_LINK__</a>
                              </p>
                """ + securityNote("Didn't request this? You can safely ignore this email — your password won't change unless you open the link above.");
        String html = brandedSecurityEmail(
                "Reset your Curio password",
                "Reset your Curio password — this link expires in 1 hour.",
                "Action required",
                body
        ).replace("__RESET_LINK__", link);
        sendViaResend(user.getEmail(), "Reset your Curio password", html);
    }

    /**
     * Shared shell for the transactional account-security emails (inverted
     * metadata strip, masthead, footer, brand palette). Both auth emails were
     * previously full copy-pasted documents — keep ALL brand chrome here so a
     * palette or masthead change is a one-place edit. The digest email is a
     * separate Thymeleaf template (digest-email.html) that mirrors the same
     * palette; update it too when changing colors.
     */
    private String brandedSecurityEmail(String title, String preheader, String badge, String bodyHtml) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>__EMAIL_TITLE__</title>
                </head>
                <body style="margin:0; padding:0; background-color:#e7ddc9; color:#14130f; font-family:'Helvetica Neue', Arial, sans-serif;">
                  <div style="display:none; max-height:0; overflow:hidden; opacity:0;">__PREHEADER__</div>
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#e7ddc9; padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px; width:100%; background-color:#f3ede1; border:1px solid #14130f;">

                          <!-- Metadata strip (inverted bar) -->
                          <tr>
                            <td style="background-color:#14130f; padding:10px 28px;">
                              <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                                <tr>
                                  <td style="font-family:'Courier New', monospace; font-size:11px; letter-spacing:2px; text-transform:uppercase; color:#f3ede1;">Curio &middot; Account Security</td>
                                  <td align="right" style="font-family:'Courier New', monospace; font-size:11px; letter-spacing:2px; text-transform:uppercase; color:#ff7a52;">__BADGE__</td>
                                </tr>
                              </table>
                            </td>
                          </tr>

                          <!-- Masthead -->
                          <tr>
                            <td style="padding:32px 28px 20px 28px; border-bottom:1px solid #14130f;">
                              <div style="font-family:Georgia, 'Times New Roman', serif; font-size:40px; line-height:1; font-weight:600; color:#14130f; letter-spacing:-1px;">Curio</div>
                              <div style="font-family:'Courier New', monospace; font-size:11px; letter-spacing:2px; text-transform:uppercase; color:#6e695b; margin-top:8px;">&mdash; A daily for AI model news</div>
                            </td>
                          </tr>

                          <!-- Body -->
                          <tr>
                            <td style="padding:32px 28px;">
                __EMAIL_BODY__
                            </td>
                          </tr>

                          <!-- Footer -->
                          <tr>
                            <td style="padding:20px 28px; border-top:1px solid #14130f; background-color:#f3ede1;">
                              <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                                <tr>
                                  <td style="font-family:'Courier New', monospace; font-size:11px; letter-spacing:1.5px; text-transform:uppercase; color:#6e695b;">&copy; 2026 Curio</td>
                                  <td align="right" style="font-family:'Courier New', monospace; font-size:11px; letter-spacing:1.5px; text-transform:uppercase; color:#6e695b;">Stay curious</td>
                                </tr>
                              </table>
                            </td>
                          </tr>

                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """
                .replace("__EMAIL_TITLE__", title)
                .replace("__PREHEADER__", preheader)
                .replace("__BADGE__", badge)
                .replace("__EMAIL_BODY__", bodyHtml);
    }

    /** The bordered security-note box both auth emails end with. */
    private String securityNote(String noteHtml) {
        return """
                              <!-- Security note -->
                              <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                                <tr>
                                  <td style="padding:14px 16px; background-color:#ece4d2; border-left:3px solid #ff4a1c;">
                                    <p style="margin:0; font-size:13px; line-height:1.55; color:#2c2a23;">
                                      __NOTE__
                                    </p>
                                  </td>
                                </tr>
                              </table>
                """.replace("__NOTE__", noteHtml);
    }

    public void sendLoginVerificationEmail(User user, String code, long ttlMinutes) {
        // A button in an email can't run JS (clients strip it), so the only way a
        // click can deliver the code is to open a web page. This link lands on the
        // verify screen with the code pre-filled (and copied there, where the
        // clipboard API actually works).
        String verifyLink = frontendUrl + "/verify?code=" +
                URLEncoder.encode(code, StandardCharsets.UTF_8);
        String body = """
                              <div style="font-family:'Courier New', monospace; font-size:11px; letter-spacing:2px; text-transform:uppercase; color:#c4290a; margin-bottom:14px;">Sign-in verification</div>
                              <h1 style="margin:0 0 16px 0; font-family:Georgia, 'Times New Roman', serif; font-size:28px; line-height:1.15; font-weight:600; color:#14130f;">Here's your code</h1>
                              <p style="margin:0 0 24px 0; font-size:15px; line-height:1.65; color:#2c2a23;">
                                Enter this code to finish signing in to your Curio account. It expires in <strong>__TTL__ minutes</strong>.
                              </p>

                              <!-- Code block -->
                              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:0 0 12px 0;">
                                <tr>
                                  <td align="center" style="background-color:#14130f; padding:22px 16px;">
                                    <div id="curio-code" style="font-family:'Courier New', monospace; font-size:38px; font-weight:700; letter-spacing:12px; color:#f3ede1; padding-left:12px;">__CODE__</div>
                                  </td>
                                </tr>
                              </table>

                              <!-- "Copy code" button. Email clients strip JS, so a literal
                                   in-message copy is impossible; instead this opens Curio with
                                   the code pre-filled. The raw code stays selectable above for
                                   anyone who'd rather copy it by hand. -->
                              <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 8px 0;">
                                <tr>
                                  <td style="border:1px solid #14130f; background-color:#f3ede1;">
                                    <a href="__VERIFY_LINK__" target="_blank" style="display:inline-block; padding:11px 24px; font-family:'Courier New', monospace; font-size:12px; font-weight:700; letter-spacing:1.5px; text-transform:uppercase; color:#14130f; text-decoration:none;">Copy code &rarr;</a>
                                  </td>
                                </tr>
                              </table>
                              <p style="margin:0 0 28px 0; font-size:12px; line-height:1.5; color:#6e695b;">Opens Curio with your code filled in. Or tap and hold the code above to copy it.</p>
                """ + securityNote("Never share this code. Curio will never ask you for it. Didn't try to sign in? You can safely ignore this email &mdash; and consider changing your password.");
        String html = brandedSecurityEmail(
                "Your Curio sign-in code",
                "Your Curio sign-in code is __CODE__ — it expires in __TTL__ minutes.",
                "Verify it's you",
                body
        )
                .replace("__VERIFY_LINK__", verifyLink)
                .replace("__CODE__", code)
                .replace("__TTL__", String.valueOf(ttlMinutes));
        sendViaResend(user.getEmail(), "Your Curio sign-in code: " + code, html);
    }

    private String renderDigestEmail(User user, Digest digest) {
        String unsubscribeToken = unsubscribeTokenService.generateToken(user.getId());

        Context context = new Context();
        context.setVariable("userName", user.getFullName());
        context.setVariable("digest", digest);
        context.setVariable("frontendUrl", frontendUrl);
        context.setVariable("backendUrl", backendUrl);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> summaries = (List<Map<String, Object>>) digest.getContent().get("summaries");
        context.setVariable("summaries", summaries != null ? summaries : List.of());
        context.setVariable("digestId", digest.getId().toString());
        context.setVariable("unsubscribeToken", unsubscribeToken);
        // Quiz generation is best-effort and can fail independently of the digest;
        // only render the "Take the quiz" CTA when the quiz actually exists, so the
        // email never links to a 404.
        context.setVariable("hasQuiz", quizPort.findByDigestId(digest.getId()).isPresent());

        return templateEngine.process("digest-email", context);
    }

    private String sendViaResend(String to, String subject, String html) {
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
                ResponseEntity<String> response = restTemplate.exchange(
                        "https://api.resend.com/emails",
                        HttpMethod.POST, entity, String.class);
                return extractProviderMessageId(response.getBody());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Email send retry interrupted", ie);
            } catch (HttpClientErrorException e) {
                // 4xx other than 429 will never succeed on retry (422 bad recipient,
                // 403 unverified domain, 400 malformed body). Fail fast to the SMTP
                // fallback instead of burning two more attempts on a certain failure.
                // 429 (rate limited) is retryable and falls through to the backoff loop.
                lastException = e;
                if (e.getStatusCode().value() != 429) {
                    log.warn("Resend API call for {} failed with non-retryable {} — not retrying",
                            to, e.getStatusCode());
                    break;
                }
                log.warn("Resend API call attempt {} rate-limited (429) for {}: {}",
                        attempt + 1, to, e.getMessage());
            } catch (Exception e) {
                lastException = e;
                log.warn("Resend API call attempt {} failed for {}: {}", attempt + 1, to, e.getMessage());
            }
        }

        log.error("Resend API call failed after {} attempts for {}", MAX_RETRIES + 1, to, lastException);

        // Last-ditch attempt via SMTP, if configured. We deliberately do not
        // retry SMTP — if Resend is down and SMTP is also down, propagating
        // the original Resend error to the caller is more useful for triage.
        String fallbackId = sendViaSmtpFallback(to, subject, html);
        if (fallbackId != null) {
            return fallbackId;
        }
        throw new RuntimeException("Failed to send email after retries", lastException);
    }

    private String sendViaSmtpFallback(String to, String subject, String html) {
        if (mailSender == null) return null;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.warn("Email delivered via SMTP fallback for {} (Resend was unavailable)", to);
            // Must be unique: email_provider_id has a UNIQUE index, so a constant
            // string would violate it on the second SMTP send of any batch (exactly
            // when Resend is down and the fallback matters most). The "smtp-fallback:"
            // prefix keeps it distinguishable from real Resend ids in webhook lookups.
            return "smtp-fallback:" + java.util.UUID.randomUUID();
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

    public void processWebhookEvent(Map<String, Object> event) {
        String type = (String) event.get("type");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) event.get("data");

        if (data == null) return;

        String emailId = (String) data.get("email_id");
        log.info("Processing email webhook event: type={}, emailId={}", type, emailId);

        Digest digest = null;
        if (emailId != null && !emailId.isBlank()) {
            digest = digestPort.findByEmailProviderId(emailId).orElse(null);
        }

        switch (type != null ? type : "") {
            case "email.opened" -> {
                log.info("Email opened: {}", emailId);
                if (digest != null && digest.getEmailOpenedAt() == null) {
                    digest.setEmailOpenedAt(LocalDateTime.now());
                    digestPort.save(digest);
                }
            }
            case "email.clicked" -> {
                log.info("Email clicked: {}", emailId);
                if (digest != null && digest.getEmailClickedAt() == null) {
                    digest.setEmailClickedAt(LocalDateTime.now());
                    digestPort.save(digest);
                }
            }
            case "email.bounced" -> {
                log.warn("Email bounced: {}", emailId);
                // Only hard/permanent bounces suppress delivery. Soft/transient
                // bounces (mailbox full, greylisting) are logged and left alone.
                if (isHardBounce(data)) {
                    suppressDelivery(digest, data, "hard bounce");
                }
            }
            case "email.complained" -> {
                log.warn("Email complaint: {}", emailId);
                // A spam complaint is unambiguous — always suppress.
                suppressDelivery(digest, data, "spam complaint");
            }
            default -> log.debug("Unhandled webhook event type: {}", type);
        }
    }

    /**
     * True only for Resend bounces classified as hard/permanent. When the payload
     * carries no bounce classification we err on the side of NOT suppressing, so a
     * transient failure never disables a valid user's delivery.
     */
    private boolean isHardBounce(Map<String, Object> data) {
        Object bounceObj = data.get("bounce");
        if (!(bounceObj instanceof Map)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> bounce = (Map<String, Object>) bounceObj;
        String bounceType = String.valueOf(bounce.getOrDefault("type", ""));
        String bounceSubType = String.valueOf(bounce.getOrDefault("subType", ""));
        return "hard".equalsIgnoreCase(bounceType) || "permanent".equalsIgnoreCase(bounceType)
                || "hard".equalsIgnoreCase(bounceSubType) || "permanent".equalsIgnoreCase(bounceSubType);
    }

    /**
     * Disable digest delivery for the recipient of a hard-failed email so the daily
     * job stops re-selecting them (protecting sender reputation). Idempotent: a no-op
     * when the user can't be resolved or delivery is already off.
     */
    private void suppressDelivery(Digest digest, Map<String, Object> data, String reason) {
        User user = null;
        // (a) prefer the digest owner when the event maps to a known digest. Re-fetch
        // by id (the FK is available without initializing the lazy proxy) so we hold a
        // managed entity to persist.
        if (digest != null && digest.getUser() != null) {
            user = userPort.findById(digest.getUser().getId()).orElse(null);
        }
        // (b) otherwise resolve by the recipient email carried in the event.
        if (user == null) {
            String email = extractRecipientEmail(data);
            if (email != null && !email.isBlank()) {
                user = userPort.findByEmail(email).orElse(null);
            }
        }
        if (user == null) {
            log.warn("Could not resolve a user to suppress delivery for ({})", reason);
            return;
        }
        if (Boolean.FALSE.equals(user.getDeliveryEnabled())) {
            return;
        }
        user.setDeliveryEnabled(false);
        userPort.save(user);
        log.warn("Disabled digest delivery for user {} due to {}", user.getId(), reason);
    }

    private String extractRecipientEmail(Map<String, Object> data) {
        Object to = data.get("to");
        if (to instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
            return list.get(0).toString();
        }
        if (to instanceof String s) {
            return s;
        }
        Object email = data.get("email");
        return (email != null) ? email.toString() : null;
    }
}
