package com.curio.auth.service;

import com.curio.auth.dto.LoginRequest;
import com.curio.auth.dto.LoginResponse;
import com.curio.auth.dto.RegisterRequest;
import com.curio.auth.dto.AuthResponse;
import com.curio.auth.dto.ResendCodeRequest;
import com.curio.auth.dto.ResendCodeResponse;
import com.curio.auth.dto.VerifyCodeRequest;
import com.curio.auth.dto.VerifyCodeResponse;
import com.curio.auth.entity.EmailVerificationCode;
import com.curio.auth.entity.PasswordResetToken;
import com.curio.auth.entity.RefreshToken;
import com.curio.auth.port.in.AuthUseCase;
import com.curio.auth.port.out.EmailVerificationCodePort;
import com.curio.auth.port.out.PasswordResetTokenPort;
import com.curio.auth.port.out.RefreshTokenPort;
import com.curio.user.port.out.UserPort;
import com.curio.user.entity.User;
import com.curio.shared.exception.UnauthorizedException;
import com.curio.shared.security.JwtTokenProvider;
import com.curio.shared.util.EmailNormalizer;
import com.curio.shared.email.EmailService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService implements AuthUseCase {

    private final UserPort userPort;
    private final RefreshTokenPort refreshTokenPort;
    private final PasswordResetTokenPort passwordResetTokenPort;
    private final EmailVerificationCodePort emailVerificationCodePort;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final RestTemplateBuilder restTemplateBuilder;

    // Email-based 2FA on email/password login. Toggleable so existing tests and
    // environments that don't want a second step can opt out; Google login never
    // goes through this (the OAuth provider already proved the identity).
    @Value("${auth.email-verification.enabled:true}")
    private boolean emailVerificationEnabled;

    @Value("${auth.email-verification.max-attempts:5}")
    private int maxVerificationAttempts;

    @Value("${auth.email-verification.code-length:6}")
    private int verificationCodeLength;

    @Value("${auth.email-verification.code-ttl-minutes:10}")
    private long codeTtlMinutes;

    // Dev only: log the code so the 2FA step is testable without working email
    // delivery. Must stay false in production (it defeats the second factor).
    @Value("${auth.email-verification.log-code:false}")
    private boolean logVerificationCode;

    // Server-enforced refresh-token lifetime. Sourced from the same property that
    // drives the cookie max-age and the refresh JWT exp so the DB row, cookie, and
    // token share one source of truth (documented 3h sliding idle timeout). Without
    // this the DB row was hardcoded to 7 days, silently ignoring JWT_REFRESH_EXPIRATION.
    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    private final SecureRandom secureRandom = new SecureRandom();

    // The OAuth client id this app was registered for. Used to validate the Google
    // ID token's `aud` claim — without this check, a token minted for ANY other
    // Google OAuth client would be accepted (token-audience confusion → takeover).
    @Value("${app.google-client-id:}")
    private String googleClientId;

    private RestTemplate restTemplate;

    @PostConstruct
    void init() {
        this.restTemplate = restTemplateBuilder.build();
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Known tradeoff: the distinct "already registered" error allows email
        // enumeration (bounded by the per-IP auth rate limit). Fixing it properly
        // requires a registration email-confirmation flow; until then the
        // Google-merge path defends the resulting squatting risk by neutralizing
        // unproven passwords (see googleLogin).
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
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
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

        if (!emailVerificationEnabled) {
            log.info("User logged in (email verification disabled): {}", user.getId());
            return LoginResponse.builder()
                    .status("AUTHENTICATED")
                    .email(user.getEmail())
                    .auth(issueTokens(user))
                    .build();
        }

        return startEmailVerification(user);
    }

    /**
     * Issues a fresh login challenge: invalidates any prior code for the user,
     * generates a new numeric code, persists its hash with the attempts budget,
     * and emails the code. Returns the opaque challenge id (no session yet).
     */
    private LoginResponse startEmailVerification(User user) {
        emailVerificationCodePort.deleteByUserId(user.getId());

        String challengeId = UUID.randomUUID() + "." + UUID.randomUUID();
        String code = generateNumericCode();

        EmailVerificationCode verification = EmailVerificationCode.builder()
                .user(user)
                .challengeHash(hashToken(challengeId))
                .codeHash(hashToken(code))
                .attemptsRemaining(maxVerificationAttempts)
                .expiresAt(LocalDateTime.now().plusMinutes(codeTtlMinutes))
                .build();
        emailVerificationCodePort.save(verification);

        sendVerificationCode(user, code);

        log.info("Login verification code issued for user {}", user.getId());
        return LoginResponse.builder()
                .status("VERIFICATION_REQUIRED")
                .challengeId(challengeId)
                .email(user.getEmail())
                .attemptsRemaining(maxVerificationAttempts)
                .expiresInSeconds(codeTtlMinutes * 60)
                .build();
    }

    @Transactional
    public VerifyCodeResponse verifyCode(VerifyCodeRequest request) {
        EmailVerificationCode verification = emailVerificationCodePort
                .findByChallengeHash(hashToken(request.getChallengeId()))
                .orElse(null);

        if (verification == null || verification.getConsumedAt() != null || verification.isExpired()) {
            if (verification != null) {
                emailVerificationCodePort.delete(verification);
            }
            return VerifyCodeResponse.builder()
                    .status("EXPIRED")
                    .attemptsRemaining(0)
                    .resetAvailable(false)
                    .message("This verification code has expired. Please sign in again.")
                    .build();
        }

        // Constant-time compare so a wrong code can't be guessed via timing.
        boolean codeMatches = MessageDigest.isEqual(
                verification.getCodeHash().getBytes(StandardCharsets.UTF_8),
                hashToken(request.getCode()).getBytes(StandardCharsets.UTF_8));

        if (codeMatches) {
            User user = verification.getUser();
            // Burn the challenge and any siblings so a code can't be replayed.
            emailVerificationCodePort.deleteByUserId(user.getId());
            log.info("Login verification succeeded for user {}", user.getId());
            return VerifyCodeResponse.builder()
                    .status("VERIFIED")
                    .attemptsRemaining(verification.getAttemptsRemaining())
                    .message("Verified.")
                    .auth(issueTokens(user))
                    .build();
        }

        // Atomic conditional decrement: parallel wrong guesses can't race the
        // read-modify-write cycle and consume more than the configured budget.
        UUID lockedUserId = verification.getUser().getId();
        int remaining = emailVerificationCodePort.decrementAttempts(verification.getId());

        if (remaining <= 0) {
            // Out of tries — consume the challenge and steer the user to a reset.
            emailVerificationCodePort.markConsumed(verification.getId());
            log.warn("Login verification locked after too many attempts for user {}", lockedUserId);
            return VerifyCodeResponse.builder()
                    .status("LOCKED")
                    .attemptsRemaining(0)
                    .resetAvailable(true)
                    .message("Too many incorrect codes. For your security, reset your password to sign in.")
                    .build();
        }

        return VerifyCodeResponse.builder()
                .status("INVALID_CODE")
                .attemptsRemaining(remaining)
                .resetAvailable(false)
                .message("That code isn't right. " + remaining
                        + (remaining == 1 ? " attempt" : " attempts") + " left.")
                .build();
    }

    @Transactional
    public ResendCodeResponse resendCode(ResendCodeRequest request) {
        EmailVerificationCode verification = emailVerificationCodePort
                .findByChallengeHash(hashToken(request.getChallengeId()))
                .orElse(null);

        if (verification == null || verification.getConsumedAt() != null || verification.isExpired()) {
            return ResendCodeResponse.builder()
                    .status("EXPIRED")
                    .message("Your sign-in session expired. Please sign in again.")
                    .build();
        }

        // Fresh code, fresh clock — but keep the remaining-attempts budget so a
        // resend loop can't be used to brute-force indefinitely.
        String code = generateNumericCode();
        verification.setCodeHash(hashToken(code));
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(codeTtlMinutes));
        emailVerificationCodePort.save(verification);

        sendVerificationCode(verification.getUser(), code);

        return ResendCodeResponse.builder()
                .status("SENT")
                .attemptsRemaining(verification.getAttemptsRemaining())
                .expiresInSeconds(codeTtlMinutes * 60)
                .message("A new code is on its way.")
                .build();
    }

    private void sendVerificationCode(User user, String code) {
        if (logVerificationCode) {
            log.warn("DEV ONLY — login verification code for {}: {}", user.getEmail(), code);
        }
        try {
            emailService.sendLoginVerificationEmail(user, code, codeTtlMinutes);
        } catch (Exception e) {
            // Best-effort, like password-reset email: a delivery failure (e.g. Resend
            // domain not verified) must NOT 500 the login or roll back the challenge,
            // otherwise sign-in is impossible. The challenge stands; the user can
            // resend, and in dev the code is logged above.
            log.error("Failed to send login verification code to {} — challenge still issued",
                    user.getEmail(), e);
        }
    }

    private String generateNumericCode() {
        StringBuilder code = new StringBuilder(verificationCodeLength);
        for (int i = 0; i < verificationCodeLength; i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }

    @Transactional
    public AuthResponse googleLogin(String idToken) {
        Map<String, Object> tokenInfo = verifyGoogleIdToken(idToken);

        String googleId = asString(tokenInfo.get("sub"));
        // Normalize so the merge lookup below matches a pre-existing password
        // account regardless of the case either side registered with.
        String email = EmailNormalizer.normalize(asString(tokenInfo.get("email")));
        String fullName = asString(tokenInfo.get("name"));
        String emailVerifiedValue = asString(tokenInfo.get("email_verified"));
        boolean emailVerified = Boolean.parseBoolean(emailVerifiedValue);

        if (googleId == null || email == null) {
            throw new UnauthorizedException("Invalid Google token");
        }
        if (!emailVerified) {
            throw new UnauthorizedException("Google account email is not verified");
        }

        User user = userPort.findByGoogleId(googleId)
                .orElseGet(() -> userPort.findByEmail(email).orElse(null));

        if (user == null) {
            user = User.builder()
                    .email(email)
                    .googleId(googleId)
                    .fullName(fullName != null ? fullName : email)
                    .isAdmin(false)
                    .emailVerified(true)
                    .build();
        } else {
            if (user.getGoogleId() == null || user.getGoogleId().isBlank()) {
                // Pre-account-hijack defense: this row was found by EMAIL, not by
                // googleId, so it may be a password account created by someone who
                // never proved they own this address (register() auto-verifies for
                // MVP). Google HAS just proven the current login owns the email, so
                // the merge is legitimate — but the pre-existing password is not.
                // Neutralize it and revoke all sessions so a squatter who
                // pre-registered the victim's email can't retain a backdoor login.
                // A legitimate dual-auth user loses nothing durable: they own the
                // inbox, so the normal password-reset flow restores password login.
                if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
                    log.warn("Google login merged into existing password account {} — "
                            + "clearing unproven password and revoking sessions", user.getId());
                    user.setPasswordHash(null);
                    refreshTokenPort.deleteByUserId(user.getId());
                }
                user.setGoogleId(googleId);
            }
            if ((user.getFullName() == null || user.getFullName().isBlank()) && fullName != null) {
                user.setFullName(fullName);
            }
            user.setEmailVerified(true);
        }

        user = userPort.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenPort.findByTokenHash(hashToken(refreshTokenValue))
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
        refreshTokenPort.findByTokenHash(hashToken(refreshTokenValue))
                .ifPresent(refreshTokenPort::delete);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userPort.findByEmail(EmailNormalizer.normalize(email)).ifPresent(user -> {
            passwordResetTokenPort.deleteByUserId(user.getId());

            String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(hashToken(rawToken))
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build();
            passwordResetTokenPort.save(resetToken);

            // Best-effort: a Resend 403 / 5xx must not fail this request — the
            // controller intentionally returns 200 for both "email exists" and
            // "doesn't exist" to avoid account-enumeration. If we propagate the
            // email-send error the caller sees a 500 and the @Transactional
            // rolls back the freshly-saved token.
            try {
                emailService.sendPasswordResetEmail(user, rawToken);
            } catch (Exception e) {
                log.error("Failed to send password reset email to {}", user.getEmail(), e);
            }
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenPort.findByTokenHash(hashToken(token))
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

    private Map<String, Object> verifyGoogleIdToken(String idToken) {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://oauth2.googleapis.com/tokeninfo")
                .queryParam("id_token", idToken)
                .toUriString();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || response.isEmpty()) {
                throw new UnauthorizedException("Invalid Google token");
            }
            validateAudienceAndIssuer(response);
            return response;
        } catch (RestClientException ex) {
            log.warn("Google token verification failed", ex);
            throw new UnauthorizedException("Invalid Google token");
        }
    }

    /**
     * Enforces that the Google ID token was minted for THIS application and by Google.
     * Google's tokeninfo endpoint validates signature/expiry but does NOT bind the token
     * to our client, so a token issued to any other Google OAuth client would otherwise
     * be accepted (account-takeover vector). Fails closed when the client id is unset.
     */
    private void validateAudienceAndIssuer(Map<String, Object> tokenInfo) {
        if (googleClientId == null || googleClientId.isBlank()
                || "not-configured".equals(googleClientId)) {
            log.warn("Google login attempted but GOOGLE_CLIENT_ID is not configured");
            throw new UnauthorizedException("Google sign-in is not enabled");
        }
        String aud = asString(tokenInfo.get("aud"));
        if (!googleClientId.equals(aud)) {
            log.warn("Rejected Google token with mismatched audience: {}", aud);
            throw new UnauthorizedException("Invalid Google token");
        }
        String iss = asString(tokenInfo.get("iss"));
        if (!"accounts.google.com".equals(iss) && !"https://accounts.google.com".equals(iss)) {
            log.warn("Rejected Google token with invalid issuer: {}", iss);
            throw new UnauthorizedException("Invalid Google token");
        }
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
                .tokenHash(hashToken(token))
                .expiresAt(LocalDateTime.now().plus(refreshExpirationMs, ChronoUnit.MILLIS))
                .build();
        refreshTokenPort.save(refreshToken);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash token", e);
        }
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }
}
