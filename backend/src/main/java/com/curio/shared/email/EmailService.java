package com.curio.shared.email;

import com.curio.shared.security.UnsubscribeTokenService;
import com.curio.news.entity.Digest;
import com.curio.news.port.out.DigestPort;
import com.curio.quiz.port.out.QuizPort;
import com.curio.shared.i18n.Language;
import com.curio.shared.port.in.EmailUseCase;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPreferencesPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Composes and sends Curio's emails in the reader's edition. Rendering is
 * Thymeleaf ({@code digest-email.html}, {@code auth-email.html}) over the
 * {@code messages*.properties} catalogs; delivery is {@link EmailTransport}.
 *
 * <p>The digest email follows the language the digest was WRITTEN in (stamped
 * on its content), so a reader who switches editions after generation still
 * gets chrome that matches the stories inside. Account emails (sign-in code,
 * password reset) follow the account's current edition.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService implements EmailUseCase {

    private final DigestPort digestPort;
    private final QuizPort quizPort;
    private final UserPreferencesPort userPreferencesPort;
    private final TemplateEngine templateEngine;
    private final UnsubscribeTokenService unsubscribeTokenService;
    private final MessageSource messageSource;
    private final EmailTransport transport;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl;

    // ---- digest ------------------------------------------------------------

    public DigestSendOutcome sendDigestEmail(User user, Digest digest) {
        // Don't swallow failures here — callers (batches, Studio) need to know
        // whether the send succeeded so their counters are honest.

        // Claim BEFORE sending: the conditional update (WHERE email_sent_at IS NULL)
        // commits immediately, so when the hourly job, an admin batch and a Studio
        // send race for the same digest exactly one wins — the user can never
        // receive the same digest twice. Losing the claim is a normal no-op.
        LocalDateTime claimedAt = LocalDateTime.now();
        if (digestPort.claimForEmailSend(digest.getId(), claimedAt) == 0) {
            log.info("Digest {} already sent or being sent by another path — skipping "
                    + "duplicate email to user {}", digest.getId(), user.getId());
            return DigestSendOutcome.ALREADY_CLAIMED;
        }

        String html = renderDigestEmail(user, digest);
        String subject = digestSubject(Language.fromDigestContent(digest.getContent()));

        String providerMessageId;
        try {
            providerMessageId = transport.send(user.getEmail(), subject, html);
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

    /** Subject line in the digest's own language, e.g. "Your Curio Daily Digest — September 16, 2026" / "Curio 데일리 다이제스트 — 2026년 9월 16일". */
    String digestSubject(Language language) {
        Locale locale = Language.orDefault(language).locale();
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern(
                messageSource.getMessage("digest.email.datePattern", null, locale), locale));
        return messageSource.getMessage("digest.email.subject", new Object[]{date}, locale);
    }

    /** Package-private so the render test can exercise the real template + catalogs without sending. */
    String renderDigestEmail(User user, Digest digest) {
        String unsubscribeToken = unsubscribeTokenService.generateToken(user.getId());
        Language language = Language.fromDigestContent(digest.getContent());
        Locale locale = language.locale();

        Context context = new Context(locale);
        context.setVariable("lang", language.code());
        String fullName = user.getFullName();
        context.setVariable("userName", (fullName != null && !fullName.isBlank())
                ? fullName : messageSource.getMessage("digest.email.reader", null, locale));
        context.setVariable("topDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern(
                messageSource.getMessage("digest.email.topDatePattern", null, locale), locale)));
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

    // ---- account emails ----------------------------------------------------

    public void sendPasswordResetEmail(User user, String resetToken) {
        Language language = languageFor(user);
        String link = frontendUrl + "/reset-password?token=" + URLEncoder.encode(resetToken, StandardCharsets.UTF_8);
        String html = renderAuthEmail("reset", language, Map.of("link", link));
        transport.send(user.getEmail(), message("auth.email.reset.subject", language), html);
    }

    public void sendLoginVerificationEmail(User user, String code, long ttlMinutes) {
        Language language = languageFor(user);
        // A button in an email can't run JS (clients strip it), so the only way a
        // click can deliver the code is to open a web page: the verify screen with
        // the code pre-filled (and copied there, where the clipboard API works).
        String verifyLink = frontendUrl + "/verify?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
        String html = renderAuthEmail("code", language, Map.of("code", code, "ttl", ttlMinutes, "verifyLink", verifyLink));
        transport.send(user.getEmail(), message("auth.email.code.subject", language, code), html);
    }

    /**
     * Renders {@code auth-email.html} for one {@code kind} ("reset" | "code") in the
     * given edition. Package-private for the render test.
     */
    String renderAuthEmail(String kind, Language language, Map<String, Object> variables) {
        Locale locale = language.locale();
        Context context = new Context(locale);
        context.setVariable("lang", language.code());
        context.setVariable("kind", kind);
        context.setVariable("title", message("auth.email." + kind + ".subject", language));
        context.setVariable("badge", message("auth.email." + kind + ".badge", language));
        Object[] preheaderArgs = "code".equals(kind)
                ? new Object[]{variables.get("code"), variables.get("ttl")}
                : new Object[0];
        context.setVariable("preheader", message("auth.email." + kind + ".preheader", language, preheaderArgs));
        variables.forEach(context::setVariable);
        return templateEngine.process("auth-email", context);
    }

    /** The account's current edition; English until the user has picked one. */
    private Language languageFor(User user) {
        return userPreferencesPort.findByUserId(user.getId())
                .map(prefs -> Language.fromCode(prefs.getLanguage()))
                .orElse(Language.DEFAULT);
    }

    private String message(String key, Language language, Object... args) {
        return messageSource.getMessage(key, args, language.locale());
    }
}
