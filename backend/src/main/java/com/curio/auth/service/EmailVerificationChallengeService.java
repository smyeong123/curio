package com.curio.auth.service;

import com.curio.auth.dto.LoginResponse;
import com.curio.auth.dto.ResendCodeResponse;
import com.curio.auth.entity.EmailVerificationCode;
import com.curio.auth.port.out.EmailVerificationCodePort;
import com.curio.shared.port.in.EmailUseCase;
import com.curio.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The second factor of email/password login: a short-lived numeric code
 * emailed to the user after their credentials check out.
 *
 * <p>Lifecycle: {@link #start} issues a challenge (hashed challenge id + hashed
 * code, attempts budget, expiry) and emails the code; {@link #verify} checks a
 * submitted code against it; {@link #resend} emails a fresh code for the same
 * challenge. The service never issues a session itself: a successful
 * verification hands the {@link User} back to {@link AuthService}, which owns
 * token issuance.
 *
 * <p>Google login never goes through this (the OAuth provider already proved
 * the identity), and the whole step can be switched off via
 * {@code auth.email-verification.enabled} for environments that don't want it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationChallengeService {

    private final EmailVerificationCodePort emailVerificationCodePort;
    private final EmailUseCase emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${auth.email-verification.enabled:true}")
    private boolean enabled;

    @Value("${auth.email-verification.max-attempts:5}")
    private int maxAttempts;

    @Value("${auth.email-verification.code-length:6}")
    private int codeLength;

    @Value("${auth.email-verification.code-ttl-minutes:10}")
    private long codeTtlMinutes;

    /**
     * Dev only: log the code so the 2FA step is testable without working email
     * delivery. Must stay false in production (it defeats the second factor).
     */
    @Value("${auth.email-verification.log-code:false}")
    private boolean logCode;

    /**
     * Outcome of a code submission. {@code user} is present only when the
     * status is {@link Status#VERIFIED}; the caller then issues the session.
     */
    public record Verification(Status status, int attemptsRemaining, boolean resetAvailable,
                               String message, User user) {

        public enum Status { VERIFIED, INVALID_CODE, LOCKED, EXPIRED }

        public boolean verified() {
            return status == Status.VERIFIED;
        }
    }

    /** Whether password logins must complete this step before a session is issued. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Issues a fresh login challenge: invalidates any prior code for the user,
     * generates a new numeric code, persists its hash with the attempts budget,
     * and emails the code. Returns the opaque challenge id (no session yet).
     */
    @Transactional
    public LoginResponse start(User user) {
        emailVerificationCodePort.deleteByUserId(user.getId());

        String challengeId = UUID.randomUUID() + "." + UUID.randomUUID();
        String code = generateNumericCode();

        EmailVerificationCode verification = EmailVerificationCode.builder()
                .user(user)
                .challengeHash(TokenHasher.hash(challengeId))
                .codeHash(TokenHasher.hash(code))
                .attemptsRemaining(maxAttempts)
                .expiresAt(LocalDateTime.now().plusMinutes(codeTtlMinutes))
                .build();
        emailVerificationCodePort.save(verification);

        sendCode(user, code);

        log.info("Login verification code issued for user {}", user.getId());
        return LoginResponse.builder()
                .status("VERIFICATION_REQUIRED")
                .challengeId(challengeId)
                .email(user.getEmail())
                .attemptsRemaining(maxAttempts)
                .expiresInSeconds(codeTtlMinutes * 60)
                .build();
    }

    /**
     * Checks a submitted code. A correct code burns the challenge (and any
     * siblings) so it cannot be replayed; a wrong code consumes one attempt,
     * and exhausting the budget locks the challenge and steers the user to a
     * password reset.
     */
    @Transactional
    public Verification verify(String challengeId, String code) {
        EmailVerificationCode verification = emailVerificationCodePort
                .findByChallengeHash(TokenHasher.hash(challengeId))
                .orElse(null);

        if (verification == null || verification.getConsumedAt() != null || verification.isExpired()) {
            if (verification != null) {
                emailVerificationCodePort.delete(verification);
            }
            return new Verification(Verification.Status.EXPIRED, 0, false,
                    "This verification code has expired. Please sign in again.", null);
        }

        // Constant-time compare so a wrong code can't be guessed via timing.
        boolean codeMatches = MessageDigest.isEqual(
                verification.getCodeHash().getBytes(StandardCharsets.UTF_8),
                TokenHasher.hash(code).getBytes(StandardCharsets.UTF_8));

        if (codeMatches) {
            User user = verification.getUser();
            emailVerificationCodePort.deleteByUserId(user.getId());
            log.info("Login verification succeeded for user {}", user.getId());
            return new Verification(Verification.Status.VERIFIED, verification.getAttemptsRemaining(),
                    false, "Verified.", user);
        }

        // Atomic conditional decrement: parallel wrong guesses can't race the
        // read-modify-write cycle and consume more than the configured budget.
        UUID lockedUserId = verification.getUser().getId();
        int remaining = emailVerificationCodePort.decrementAttempts(verification.getId());

        if (remaining <= 0) {
            emailVerificationCodePort.markConsumed(verification.getId());
            log.warn("Login verification locked after too many attempts for user {}", lockedUserId);
            return new Verification(Verification.Status.LOCKED, 0, true,
                    "Too many incorrect codes. For your security, reset your password to sign in.", null);
        }

        return new Verification(Verification.Status.INVALID_CODE, remaining, false,
                "That code isn't right. " + remaining
                        + (remaining == 1 ? " attempt" : " attempts") + " left.", null);
    }

    /**
     * Emails a fresh code with a fresh expiry for an in-flight challenge. The
     * remaining-attempts budget is deliberately kept so a resend loop cannot
     * become an unbounded brute-force channel.
     */
    @Transactional
    public ResendCodeResponse resend(String challengeId) {
        EmailVerificationCode verification = emailVerificationCodePort
                .findByChallengeHash(TokenHasher.hash(challengeId))
                .orElse(null);

        if (verification == null || verification.getConsumedAt() != null || verification.isExpired()) {
            return ResendCodeResponse.builder()
                    .status("EXPIRED")
                    .message("Your sign-in session expired. Please sign in again.")
                    .build();
        }

        String code = generateNumericCode();
        verification.setCodeHash(TokenHasher.hash(code));
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(codeTtlMinutes));
        emailVerificationCodePort.save(verification);

        sendCode(verification.getUser(), code);

        return ResendCodeResponse.builder()
                .status("SENT")
                .attemptsRemaining(verification.getAttemptsRemaining())
                .expiresInSeconds(codeTtlMinutes * 60)
                .message("A new code is on its way.")
                .build();
    }

    private void sendCode(User user, String code) {
        if (logCode) {
            log.warn("DEV ONLY — login verification code for {}: {}", user.getEmail(), code);
        }
        try {
            emailService.sendLoginVerificationEmail(user, code, codeTtlMinutes);
        } catch (Exception e) {
            // Best-effort, like the password-reset email: a delivery failure (e.g.
            // Resend domain not verified) must NOT 500 the login or roll back the
            // challenge, otherwise sign-in is impossible. The challenge stands; the
            // user can resend, and in dev the code is logged above.
            log.error("Failed to send login verification code to {} — challenge still issued",
                    user.getEmail(), e);
        }
    }

    private String generateNumericCode() {
        StringBuilder code = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }
}
