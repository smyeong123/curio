package com.curio.auth.service;

import com.curio.admin.port.in.AuditLogUseCase;
import com.curio.auth.dto.AuthResponse;
import com.curio.auth.dto.LoginRequest;
import com.curio.auth.dto.LoginResponse;
import com.curio.auth.dto.RegisterRequest;
import com.curio.auth.dto.RegisterResponse;
import com.curio.auth.dto.ResendCodeRequest;
import com.curio.auth.dto.ResendCodeResponse;
import com.curio.auth.dto.VerifyCodeRequest;
import com.curio.auth.dto.VerifyCodeResponse;
import com.curio.auth.entity.EmailConfirmationToken;
import com.curio.auth.entity.PasswordResetToken;
import com.curio.auth.entity.RefreshToken;
import com.curio.auth.port.in.AuthUseCase;
import com.curio.auth.port.out.EmailConfirmationTokenPort;
import com.curio.auth.port.out.PasswordResetTokenPort;
import com.curio.auth.port.out.RefreshTokenPort;
import com.curio.auth.service.EmailVerificationChallengeService.Verification;
import com.curio.shared.exception.EmailNotConfirmedException;
import com.curio.shared.exception.InvalidConfirmationTokenException;
import com.curio.shared.exception.UnauthorizedException;
import com.curio.shared.i18n.Language;
import com.curio.shared.port.in.EmailUseCase;
import com.curio.shared.security.JwtTokenProvider;
import com.curio.shared.util.EmailNormalizer;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Account and session policy: registration + sign-up email confirmation,
 * password login, session issuance/rotation, and password reset. The email second factor lives in
 * {@link EmailVerificationChallengeService} and Google token validation in
 * {@link GoogleIdTokenVerifier}; this class decides what an authenticated
 * identity is allowed to become (a new account, a merged account, a session).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService implements AuthUseCase {

    private final UserPort userPort;
    private final RefreshTokenPort refreshTokenPort;
    private final PasswordResetTokenPort passwordResetTokenPort;
    private final EmailConfirmationTokenPort emailConfirmationTokenPort;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final EmailUseCase emailService;
    private final EmailVerificationChallengeService challengeService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final AuditLogUseCase auditLog;

    static final String AUDIT_REFRESH_TOKEN_REUSE = "REFRESH_TOKEN_REUSE_DETECTED";

    /**
     * How long a rotated refresh token may be replayed before it counts as theft.
     * Covers two tabs racing the same cookie; past it, every session is revoked.
     */
    @Value("${auth.refresh-token.reuse-grace-seconds:30}")
    private long refreshReuseGraceSeconds;

    /**
     * Server-enforced refresh-token lifetime. Sourced from the same property that
     * drives the cookie max-age and the refresh JWT exp so the DB row, cookie, and
     * token share one source of truth (the documented 3h sliding idle timeout).
     */
    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    /** Lifetime of a sign-up confirmation link; quoted in the email copy. */
    @Value("${auth.email-confirmation.token-ttl-hours:24}")
    private long confirmationTokenTtlHours = 24;

    /**
     * Starts a password sign-up. No session is issued: the account stays
     * unconfirmed (no login) until the emailed link is opened ({@link #confirmEmail}).
     *
     * <p>Every case returns the same {@link RegisterResponse} (the controller
     * answers 202), so registration no longer tells anyone which addresses have
     * accounts:
     * <ul>
     *   <li>new address: create an unconfirmed account and mail a link;</li>
     *   <li>already CONFIRMED account: change nothing and send nothing;</li>
     *   <li>still UNCONFIRMED account: neither the old nor the new password is
     *       proven, so the newest sign-up wins (new password and name, old links
     *       revoked, a fresh link mailed). Squatting an address therefore blocks
     *       nobody: the real owner just signs up again and confirms.</li>
     * </ul>
     * The password is hashed up front in every case so the confirmed-account path
     * doesn't skip the slow bcrypt step and answer measurably faster.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // Canonicalize so Victim@x.com and victim@x.com can never become two rows
        // for one real mailbox (which would defeat the Google-merge anti-squat defense).
        String email = EmailNormalizer.normalize(request.getEmail());
        String passwordHash = passwordEncoder.encode(request.getPassword());
        Language language = Language.fromCode(request.getLanguage());

        User existing = userPort.findByEmail(email).orElse(null);
        if (existing != null) {
            if (Boolean.TRUE.equals(existing.getEmailVerified())) {
                log.info("Sign-up for already-confirmed account {}: nothing changed", existing.getId());
                return RegisterResponse.confirmationSent();
            }
            existing.setPasswordHash(passwordHash);
            existing.setFullName(request.getFullName());
            User user = userPort.save(existing);
            log.info("Sign-up repeated for unconfirmed account {}: password replaced, new link sent", user.getId());
            sendConfirmation(user, language);
            return RegisterResponse.confirmationSent();
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordHash)
                .fullName(request.getFullName())
                .isAdmin(false)
                .emailVerified(false)
                .build();

        try {
            user = userPort.save(user);
        } catch (DataIntegrityViolationException e) {
            // Lost the unique-email race to a concurrent sign-up for the same
            // address. That request mails its own link; answer the same as always.
            log.info("Concurrent sign-up for the same email lost the insert race");
            return RegisterResponse.confirmationSent();
        }
        log.info("New user registered (unconfirmed): {}", user.getId());
        sendConfirmation(user, language);
        return RegisterResponse.confirmationSent();
    }

    /**
     * Opens a sign-up confirmation link: marks the email confirmed and signs the
     * user in (AuthService alone issues sessions). Unknown, expired and used links
     * all fail the same way ({@link InvalidConfirmationTokenException}).
     */
    @Transactional
    public AuthResponse confirmEmail(String rawToken) {
        EmailConfirmationToken token = emailConfirmationTokenPort.findByTokenHash(TokenHasher.hash(rawToken))
                .orElseThrow(InvalidConfirmationTokenException::new);
        if (token.getUsedAt() != null || token.isExpired()) {
            throw new InvalidConfirmationTokenException();
        }
        // Single use even under a double click: only one request wins the claim.
        if (emailConfirmationTokenPort.markUsed(token.getId(), LocalDateTime.now()) == 0) {
            throw new InvalidConfirmationTokenException();
        }

        User user = token.getUser();
        user.setEmailVerified(true);
        user.setEmailConfirmedAt(LocalDateTime.now());
        user = userPort.save(user);
        log.info("Email confirmed for user {}", user.getId());
        return issueTokens(user);
    }

    /**
     * Mails a fresh confirmation link, but only for an existing UNCONFIRMED
     * account. Unknown and confirmed addresses are a silent no-op; the controller
     * answers 202 either way, so this can't be used to probe for accounts.
     */
    @Transactional
    public void resendConfirmation(String email, String languageCode) {
        Language language = Language.fromCode(languageCode);
        userPort.findByEmail(EmailNormalizer.normalize(email))
                .filter(user -> !Boolean.TRUE.equals(user.getEmailVerified()))
                .ifPresent(user -> sendConfirmation(user, language));
    }

    /** Replaces any outstanding link with a new one and mails it (best effort). */
    private void sendConfirmation(User user, Language language) {
        emailConfirmationTokenPort.deleteByUserId(user.getId());

        String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
        emailConfirmationTokenPort.save(EmailConfirmationToken.builder()
                .user(user)
                .tokenHash(TokenHasher.hash(rawToken))
                .expiresAt(LocalDateTime.now().plusHours(confirmationTokenTtlHours))
                .build());

        // Best effort, like the reset email: a provider error must not 500 the
        // request (that would differ from the no-account answer) or roll back the
        // account. The user can ask for another link from the sign-in screen.
        try {
            emailService.sendEmailConfirmation(user, rawToken, confirmationTokenTtlHours, language);
        } catch (Exception e) {
            log.error("Failed to send sign-up confirmation email to user {}", user.getId(), e);
        }
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = EmailNormalizer.normalize(request.getEmail());
        // Throws on bad credentials — must pass before we ever issue a code.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        User user = userPort.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        // Checked only AFTER the password passed (UserDetailsAdapter.isEnabled is
        // always true for that reason), so it tells nothing to someone without it.
        // No 2FA code is sent: the account can't sign in until it is confirmed.
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new EmailNotConfirmedException();
        }

        if (!challengeService.isEnabled()) {
            log.info("User logged in (email verification disabled): {}", user.getId());
            return LoginResponse.builder()
                    .status("AUTHENTICATED")
                    .email(user.getEmail())
                    .auth(issueTokens(user))
                    .build();
        }

        return challengeService.start(user);
    }

    @Transactional
    public VerifyCodeResponse verifyCode(VerifyCodeRequest request) {
        Verification outcome = challengeService.verify(request.getChallengeId(), request.getCode());
        return VerifyCodeResponse.builder()
                .status(outcome.status().name())
                .attemptsRemaining(outcome.attemptsRemaining())
                .resetAvailable(outcome.resetAvailable())
                .message(outcome.message())
                .auth(outcome.verified() ? issueTokens(outcome.user()) : null)
                .build();
    }

    @Transactional
    public ResendCodeResponse resendCode(ResendCodeRequest request) {
        return challengeService.resend(request.getChallengeId());
    }

    @Transactional
    public AuthResponse googleLogin(String idToken) {
        GoogleIdentity identity = googleIdTokenVerifier.verify(idToken);
        if (!identity.emailVerified()) {
            throw new UnauthorizedException("Google account email is not verified");
        }
        // Normalize so the merge lookup matches a pre-existing password account
        // regardless of the case either side registered with.
        String email = EmailNormalizer.normalize(identity.email());

        User user = userPort.findByGoogleId(identity.sub())
                .orElseGet(() -> userPort.findByEmail(email).orElse(null));

        if (user == null) {
            user = User.builder()
                    .email(email)
                    .googleId(identity.sub())
                    .fullName(identity.name() != null ? identity.name() : email)
                    .isAdmin(false)
                    .emailVerified(true)
                    .build();
        } else {
            linkGoogleIdentity(user, identity);
        }

        user = userPort.save(user);
        return issueTokens(user);
    }

    /**
     * Binds a Google identity to an existing account found by googleId or EMAIL.
     *
     * <p>Only a password account whose owner PROVED inbox ownership
     * ({@code emailConfirmedAt} set: sign-up link opened, or a password reset
     * completed) is simply linked, keeping its password and sessions. Google has
     * just proven the current login owns the same inbox, so both are the owner.
     *
     * <p>Any other password account may belong to a squatter who never proved
     * they own this address: an unconfirmed sign-up, or a legacy account whose
     * {@code emailVerified} is true only because the old MVP register()
     * auto-verified it (emailVerified alone proves nothing). Google HAS just
     * proven the current login owns the email, so the merge is legitimate, but
     * the pre-existing password is not: neutralize it, drop its pending
     * confirmation links and revoke all sessions so the squatter keeps no
     * backdoor login. A legitimate owner loses nothing durable: they own the
     * inbox, so the normal password-reset flow restores password login.
     */
    private void linkGoogleIdentity(User user, GoogleIdentity identity) {
        if (user.getGoogleId() == null || user.getGoogleId().isBlank()) {
            boolean inboxProven = user.getEmailConfirmedAt() != null;
            if (!inboxProven && user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
                log.warn("Google login merged into password account {} with no proven inbox: "
                        + "clearing unproven password and revoking sessions", user.getId());
                user.setPasswordHash(null);
                emailConfirmationTokenPort.deleteByUserId(user.getId());
                refreshTokenPort.deleteByUserId(user.getId());
            }
            user.setGoogleId(identity.sub());
        }
        if ((user.getFullName() == null || user.getFullName().isBlank()) && identity.name() != null) {
            user.setFullName(identity.name());
        }
        user.setEmailVerified(true);
    }

    /**
     * Rotates a refresh token, with reuse detection (OWASP / RFC 6819 5.2.2.3).
     *
     * <p>Rotation marks the presented row {@code rotated_at} (atomic conditional
     * UPDATE) instead of deleting it, so a replay is recognisable until the row
     * expires. A rotated token presented again is always a 401; past the grace
     * window it is treated as theft and every session of the user is revoked.
     * Inside the window it is the benign two-tabs race, so nothing else is
     * touched. The successor is never handed out again: only its hash is stored.
     *
     * <p>{@code noRollbackFor}: the expired-row delete and the theft revocation
     * must commit even though the request ends in a 401.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenPort.findByTokenHash(TokenHasher.hash(refreshTokenValue))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        // Expiry first: an expired token is dead regardless of history, not "reuse".
        if (refreshToken.isExpired()) {
            refreshTokenPort.delete(refreshToken);
            throw new UnauthorizedException("Refresh token expired");
        }

        LocalDateTime now = LocalDateTime.now();
        if (refreshToken.isRotated()) {
            throw rejectRotatedToken(refreshToken, now);
        }

        // Lost a race with a concurrent refresh of this same token that committed
        // first, i.e. it was rotated an instant ago: inside the grace window.
        if (refreshTokenPort.markRotated(refreshToken.getId(), now) == 0) {
            throw new UnauthorizedException("Refresh token already rotated");
        }

        return issueTokens(refreshToken.getUser());
    }

    private UnauthorizedException rejectRotatedToken(RefreshToken refreshToken, LocalDateTime now) {
        long secondsSinceRotation = Duration.between(refreshToken.getRotatedAt(), now).toSeconds();
        if (secondsSinceRotation <= refreshReuseGraceSeconds) {
            return new UnauthorizedException("Refresh token already rotated");
        }

        UUID userId = refreshToken.getUser().getId();
        refreshTokenPort.deleteByUserId(userId);
        // Never log the token or its hash: the user id and timing are enough to triage.
        log.warn("Refresh token reuse detected for user {} ({}s after rotation) — revoked all sessions",
                userId, secondsSinceRotation);
        auditLog.record(AUDIT_REFRESH_TOKEN_REUSE, "USER", userId.toString(),
                Map.of("secondsSinceRotation", secondsSinceRotation,
                        "graceSeconds", refreshReuseGraceSeconds));
        return new UnauthorizedException("Invalid refresh token");
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            return;
        }
        refreshTokenPort.findByTokenHash(TokenHasher.hash(refreshTokenValue))
                .ifPresent(refreshTokenPort::delete);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userPort.findByEmail(EmailNormalizer.normalize(email)).ifPresent(user -> {
            passwordResetTokenPort.deleteByUserId(user.getId());

            String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(TokenHasher.hash(rawToken))
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build();
            passwordResetTokenPort.save(resetToken);

            // Best-effort: a Resend 403 / 5xx must not fail this request — the
            // controller intentionally returns 200 for both "email exists" and
            // "doesn't exist" to avoid account-enumeration. Propagating the
            // email-send error would 500 the caller and roll back the
            // freshly-saved token.
            try {
                emailService.sendPasswordResetEmail(user, rawToken);
            } catch (Exception e) {
                log.error("Failed to send password reset email to {}", user.getEmail(), e);
            }
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenPort.findByTokenHash(TokenHasher.hash(token))
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired reset token"));

        if (resetToken.getUsedAt() != null || resetToken.isExpired()) {
            throw new UnauthorizedException("Invalid or expired reset token");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setEmailVerified(true);
        // Opening the reset link proves inbox ownership (trusted by the Google merge).
        user.setEmailConfirmedAt(LocalDateTime.now());
        userPort.save(user);
        // Owning the inbox is the recovery path out of a 2FA lockout.
        userPort.resetLoginCodeFailures(user.getId());

        // Revoke all sessions so a stolen refresh token can't survive account recovery
        refreshTokenPort.deleteByUserId(user.getId());

        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenPort.save(resetToken);
        log.info("Password reset completed for user: {}", user.getId());
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        saveRefreshToken(user, refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .isAdmin(user.getIsAdmin())
                .build();
    }

    private void saveRefreshToken(User user, String token) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(TokenHasher.hash(token))
                .expiresAt(LocalDateTime.now().plus(refreshExpirationMs, ChronoUnit.MILLIS))
                .build();
        refreshTokenPort.save(refreshToken);
    }
}
