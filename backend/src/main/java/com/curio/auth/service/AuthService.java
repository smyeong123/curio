package com.curio.auth.service;

import com.curio.auth.dto.AuthResponse;
import com.curio.auth.dto.LoginRequest;
import com.curio.auth.dto.LoginResponse;
import com.curio.auth.dto.RegisterRequest;
import com.curio.auth.dto.ResendCodeRequest;
import com.curio.auth.dto.ResendCodeResponse;
import com.curio.auth.dto.VerifyCodeRequest;
import com.curio.auth.dto.VerifyCodeResponse;
import com.curio.auth.entity.PasswordResetToken;
import com.curio.auth.entity.RefreshToken;
import com.curio.auth.port.in.AuthUseCase;
import com.curio.auth.port.out.PasswordResetTokenPort;
import com.curio.auth.port.out.RefreshTokenPort;
import com.curio.auth.service.EmailVerificationChallengeService.Verification;
import com.curio.shared.exception.UnauthorizedException;
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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Account and session policy: registration, password login, session
 * issuance/rotation, and password reset. The email second factor lives in
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
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final EmailUseCase emailService;
    private final EmailVerificationChallengeService challengeService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    /**
     * Server-enforced refresh-token lifetime. Sourced from the same property that
     * drives the cookie max-age and the refresh JWT exp so the DB row, cookie, and
     * token share one source of truth (the documented 3h sliding idle timeout).
     */
    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Known tradeoff: the distinct "already registered" error allows email
        // enumeration (bounded by the per-IP auth rate limit). Closing it needs a
        // registration email-confirmation flow; meanwhile the Google-merge path
        // defends the resulting squatting risk by neutralizing unproven passwords
        // (see linkGoogleIdentity).
        // Canonicalize so Victim@x.com and victim@x.com can never become two rows
        // for one real mailbox (which would defeat the Google-merge anti-squat defense).
        String email = EmailNormalizer.normalize(request.getEmail());
        if (userPort.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .isAdmin(false)
                .emailVerified(true) // Auto-verify for MVP
                .build();

        try {
            user = userPort.save(user);
        } catch (DataIntegrityViolationException e) {
            // Lost the unique-email race to a concurrent registration — same
            // outcome as the pre-check above, not a server error.
            throw new IllegalArgumentException("Email already registered");
        }
        log.info("New user registered: {}", user.getId());

        return issueTokens(user);
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
     * Binds a Google identity to an existing account. When the account was found
     * by EMAIL rather than by googleId it may be a password account created by
     * someone who never proved they own this address (register() auto-verifies
     * for MVP). Google HAS just proven the current login owns the email, so the
     * merge is legitimate — but the pre-existing password is not: neutralize it
     * and revoke all sessions so a squatter who pre-registered the victim's email
     * keeps no backdoor login. A legitimate dual-auth user loses nothing durable:
     * they own the inbox, so the normal password-reset flow restores password login.
     */
    private void linkGoogleIdentity(User user, GoogleIdentity identity) {
        if (user.getGoogleId() == null || user.getGoogleId().isBlank()) {
            if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
                log.warn("Google login merged into existing password account {} — "
                        + "clearing unproven password and revoking sessions", user.getId());
                user.setPasswordHash(null);
                refreshTokenPort.deleteByUserId(user.getId());
            }
            user.setGoogleId(identity.sub());
        }
        if ((user.getFullName() == null || user.getFullName().isBlank()) && identity.name() != null) {
            user.setFullName(identity.name());
        }
        user.setEmailVerified(true);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenPort.findByTokenHash(TokenHasher.hash(refreshTokenValue))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (refreshToken.isExpired()) {
            refreshTokenPort.delete(refreshToken);
            throw new UnauthorizedException("Refresh token expired");
        }

        User user = refreshToken.getUser();
        refreshTokenPort.delete(refreshToken);
        return issueTokens(user);
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
        userPort.save(user);

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
