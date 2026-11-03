package com.curio.auth.service;

import com.curio.admin.port.in.AuditLogUseCase;
import com.curio.auth.dto.AuthResponse;
import com.curio.auth.dto.LoginRequest;
import com.curio.auth.dto.LoginResponse;
import com.curio.auth.dto.RegisterRequest;
import com.curio.auth.dto.RegisterResponse;
import com.curio.auth.dto.VerifyCodeRequest;
import com.curio.auth.dto.VerifyCodeResponse;
import com.curio.auth.entity.EmailConfirmationToken;
import com.curio.auth.entity.EmailVerificationCode;
import com.curio.auth.entity.PasswordResetToken;
import com.curio.auth.entity.RefreshToken;
import com.curio.auth.port.out.EmailConfirmationTokenPort;
import com.curio.auth.port.out.EmailVerificationCodePort;
import com.curio.auth.port.out.PasswordResetTokenPort;
import com.curio.auth.port.out.RefreshTokenPort;
import com.curio.shared.email.EmailService;
import com.curio.shared.exception.EmailNotConfirmedException;
import com.curio.shared.exception.InvalidConfirmationTokenException;
import com.curio.shared.exception.UnauthorizedException;
import com.curio.shared.i18n.Language;
import com.curio.shared.security.JwtTokenProvider;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Account/session policy tests. Google token validation is mocked out via
 * {@link GoogleIdTokenVerifier} (see GoogleIdTokenVerifierTest); the email
 * second factor runs for real against the mocked port so the login/verify
 * contract the frontend reads is asserted end to end.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserPort userPort;
    @Mock private RefreshTokenPort refreshTokenPort;
    @Mock private PasswordResetTokenPort passwordResetTokenPort;
    @Mock private EmailConfirmationTokenPort emailConfirmationTokenPort;
    @Mock private EmailVerificationCodePort emailVerificationCodePort;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private EmailService emailService;
    @Mock private GoogleIdTokenVerifier googleIdTokenVerifier;
    @Mock private AuditLogUseCase auditLog;

    private EmailVerificationChallengeService challengeService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        challengeService = new EmailVerificationChallengeService(emailVerificationCodePort, emailService, userPort);
        // @Value fields aren't injected in a plain Mockito unit test — set them by hand.
        ReflectionTestUtils.setField(challengeService, "enabled", true);
        ReflectionTestUtils.setField(challengeService, "maxAttempts", 5);
        ReflectionTestUtils.setField(challengeService, "maxAccountFailures", 10);
        ReflectionTestUtils.setField(challengeService, "codeLength", 6);
        ReflectionTestUtils.setField(challengeService, "codeTtlMinutes", 10L);

        authService = new AuthService(userPort, refreshTokenPort, passwordResetTokenPort,
                emailConfirmationTokenPort, passwordEncoder, jwtTokenProvider, authenticationManager, emailService,
                challengeService, googleIdTokenVerifier, auditLog);
        ReflectionTestUtils.setField(authService, "refreshReuseGraceSeconds", 30L);
    }

    /** Mirrors TokenHasher so tests can stage a known code hash independently of it. */
    private static String sha(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static GoogleIdentity googleIdentity(String email) {
        return new GoogleIdentity("google-sub-123", email, "Google User", true);
    }

    // --- register: no session, one answer for every case ---

    private static RegisterRequest registerRequest(String email, String password) {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword(password);
        req.setFullName("New User");
        return req;
    }

    @Test
    void register_newEmail_createsUnconfirmedAccount_mailsLink_andIssuesNoSession() {
        RegisterRequest req = registerRequest("new@example.com", "Pa$$word123");
        req.setLanguage("ko");
        when(passwordEncoder.encode("Pa$$word123")).thenReturn("HASHED");
        when(userPort.findByEmail("new@example.com")).thenReturn(Optional.empty());
        User saved = User.builder().id(UUID.randomUUID()).email("new@example.com")
                .fullName("New User").isAdmin(false).build();
        when(userPort.save(any(User.class))).thenReturn(saved);

        RegisterResponse response = authService.register(req);

        assertThat(response.status()).isEqualTo("confirmation_sent");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userPort).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("HASHED");
        assertThat(userCaptor.getValue().getEmailVerified()).isFalse();

        // Only the hash is stored; the raw token goes out in the email.
        ArgumentCaptor<EmailConfirmationToken> tokenCaptor = ArgumentCaptor.forClass(EmailConfirmationToken.class);
        verify(emailConfirmationTokenPort).save(tokenCaptor.capture());
        ArgumentCaptor<String> rawCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailConfirmation(eq(saved), rawCaptor.capture(), eq(24L), eq(Language.KO));
        assertThat(tokenCaptor.getValue().getTokenHash()).isEqualTo(sha(rawCaptor.getValue()));
        assertThat(tokenCaptor.getValue().getTokenHash()).isNotEqualTo(rawCaptor.getValue());
        assertThat(tokenCaptor.getValue().getExpiresAt())
                .isBetween(LocalDateTime.now().plusHours(23), LocalDateTime.now().plusHours(25));
        verify(emailConfirmationTokenPort).deleteByUserId(saved.getId());

        verifyNoInteractions(jwtTokenProvider);
        verify(refreshTokenPort, never()).save(any());
    }

    @Test
    void register_alreadyConfirmedEmail_changesNothing_sendsNothing_answersTheSame() {
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
        User confirmed = User.builder().id(UUID.randomUUID()).email("dup@example.com")
                .passwordHash("OWNER-HASH").emailVerified(true).build();
        when(userPort.findByEmail("dup@example.com")).thenReturn(Optional.of(confirmed));

        RegisterResponse response = authService.register(registerRequest("dup@example.com", "Pa$$word123"));

        assertThat(response).isEqualTo(RegisterResponse.confirmationSent());
        assertThat(confirmed.getPasswordHash()).isEqualTo("OWNER-HASH");
        // Hashed anyway so this path costs the same bcrypt time as the others.
        verify(passwordEncoder).encode("Pa$$word123");
        verify(userPort, never()).save(any());
        verifyNoInteractions(emailConfirmationTokenPort, emailService, jwtTokenProvider);
        verify(refreshTokenPort, never()).save(any());
    }

    @Test
    void register_unconfirmedEmail_replacesPassword_revokesOldLinks_andMailsAFreshOne() {
        when(passwordEncoder.encode("NewPa$$word")).thenReturn("NEW-HASH");
        User pending = User.builder().id(UUID.randomUUID()).email("pending@example.com")
                .passwordHash("SQUATTER-HASH").fullName("Squatter").emailVerified(false).build();
        when(userPort.findByEmail("pending@example.com")).thenReturn(Optional.of(pending));
        when(userPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterResponse response = authService.register(registerRequest("pending@example.com", "NewPa$$word"));

        assertThat(response.status()).isEqualTo("confirmation_sent");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userPort).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("NEW-HASH");
        assertThat(userCaptor.getValue().getFullName()).isEqualTo("New User");
        assertThat(userCaptor.getValue().getEmailVerified()).isFalse();
        verify(emailConfirmationTokenPort).deleteByUserId(pending.getId());
        verify(emailConfirmationTokenPort).save(any(EmailConfirmationToken.class));
        verify(emailService).sendEmailConfirmation(eq(pending), anyString(), eq(24L), eq(Language.EN));
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void register_normalizesEmail_soCaseVariantCollidesAndIsStoredLowercase() {
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
        // The lookup must run against the normalized form — a case variant of an
        // existing address must NOT slip through as a fresh account.
        when(userPort.findByEmail("victim@gmail.com")).thenReturn(Optional.empty());
        when(userPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(registerRequest("  Victim@Gmail.COM ", "Pa$$word123"));

        verify(userPort).findByEmail("victim@gmail.com");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userPort).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("victim@gmail.com");
    }

    @Test
    void register_lostUniqueEmailRace_answersTheSame_withoutMailing() {
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
        when(userPort.findByEmail("race@example.com")).thenReturn(Optional.empty());
        when(userPort.save(any(User.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));

        RegisterResponse response = authService.register(registerRequest("race@example.com", "Pa$$word123"));

        assertThat(response.status()).isEqualTo("confirmation_sent");
        verifyNoInteractions(emailConfirmationTokenPort, emailService);
    }

    @Test
    void register_mailFailure_isSwallowed_soTheAnswerStaysUniform() {
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
        when(userPort.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Resend 500"))
                .when(emailService).sendEmailConfirmation(any(), anyString(), anyLong(), any());

        assertThat(authService.register(registerRequest("new@example.com", "Pa$$word123")).status())
                .isEqualTo("confirmation_sent");
    }

    // --- confirmEmail ---

    private EmailConfirmationToken confirmationToken(User user, String raw, LocalDateTime expiresAt,
                                                     LocalDateTime usedAt) {
        return EmailConfirmationToken.builder().id(UUID.randomUUID()).user(user)
                .tokenHash(sha(raw)).expiresAt(expiresAt).usedAt(usedAt).build();
    }

    @Test
    void confirmEmail_validToken_confirmsTheEmail_consumesTheToken_andIssuesASession() {
        User user = User.builder().id(UUID.randomUUID()).email("u@e.com").fullName("U")
                .isAdmin(false).emailVerified(false).build();
        EmailConfirmationToken token = confirmationToken(user, "raw", LocalDateTime.now().plusHours(1), null);
        when(emailConfirmationTokenPort.findByTokenHash(sha("raw"))).thenReturn(Optional.of(token));
        when(emailConfirmationTokenPort.markUsed(eq(token.getId()), any(LocalDateTime.class))).thenReturn(1);
        when(userPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateToken(user)).thenReturn("a");
        when(jwtTokenProvider.generateRefreshToken(user)).thenReturn("r");

        AuthResponse response = authService.confirmEmail("raw");

        assertThat(response.getAccessToken()).isEqualTo("a");
        assertThat(response.getRefreshToken()).isEqualTo("r");
        assertThat(response.getEmail()).isEqualTo("u@e.com");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userPort).save(captor.capture());
        assertThat(captor.getValue().getEmailVerified()).isTrue();
        assertThat(captor.getValue().getEmailConfirmedAt()).isNotNull();
        verify(refreshTokenPort).save(any(RefreshToken.class));
    }

    @Test
    void confirmEmail_expiredToken_isRejected() {
        User user = User.builder().id(UUID.randomUUID()).email("u@e.com").build();
        when(emailConfirmationTokenPort.findByTokenHash(sha("raw")))
                .thenReturn(Optional.of(confirmationToken(user, "raw", LocalDateTime.now().minusMinutes(1), null)));

        assertThatThrownBy(() -> authService.confirmEmail("raw"))
                .isInstanceOf(InvalidConfirmationTokenException.class);
        verify(emailConfirmationTokenPort, never()).markUsed(any(), any());
        verify(userPort, never()).save(any());
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void confirmEmail_usedToken_isRejected() {
        User user = User.builder().id(UUID.randomUUID()).email("u@e.com").build();
        when(emailConfirmationTokenPort.findByTokenHash(sha("raw"))).thenReturn(Optional.of(
                confirmationToken(user, "raw", LocalDateTime.now().plusHours(1), LocalDateTime.now().minusMinutes(5))));

        assertThatThrownBy(() -> authService.confirmEmail("raw"))
                .isInstanceOf(InvalidConfirmationTokenException.class);
        verify(userPort, never()).save(any());
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void confirmEmail_lostTheSingleUseClaim_toAConcurrentConfirm_isRejected() {
        User user = User.builder().id(UUID.randomUUID()).email("u@e.com").build();
        EmailConfirmationToken token = confirmationToken(user, "raw", LocalDateTime.now().plusHours(1), null);
        when(emailConfirmationTokenPort.findByTokenHash(sha("raw"))).thenReturn(Optional.of(token));
        when(emailConfirmationTokenPort.markUsed(eq(token.getId()), any(LocalDateTime.class))).thenReturn(0);

        assertThatThrownBy(() -> authService.confirmEmail("raw"))
                .isInstanceOf(InvalidConfirmationTokenException.class);
        verify(userPort, never()).save(any());
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void confirmEmail_unknownToken_isRejected() {
        when(emailConfirmationTokenPort.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.confirmEmail("nope"))
                .isInstanceOf(InvalidConfirmationTokenException.class);
        verifyNoInteractions(jwtTokenProvider);
    }

    // --- resendConfirmation: only unconfirmed accounts get mail, nobody can tell ---

    @Test
    void resendConfirmation_unknownEmail_isASilentNoOp() {
        when(userPort.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        authService.resendConfirmation(" Ghost@Example.com ", "en");

        verifyNoInteractions(emailConfirmationTokenPort, emailService);
    }

    @Test
    void resendConfirmation_confirmedAccount_isASilentNoOp() {
        User confirmed = User.builder().id(UUID.randomUUID()).email("c@example.com").emailVerified(true).build();
        when(userPort.findByEmail("c@example.com")).thenReturn(Optional.of(confirmed));

        authService.resendConfirmation("c@example.com", "en");

        verifyNoInteractions(emailConfirmationTokenPort, emailService);
    }

    @Test
    void resendConfirmation_unconfirmedAccount_replacesTheLink_andMailsIt() {
        User pending = User.builder().id(UUID.randomUUID()).email("p@example.com").emailVerified(false).build();
        when(userPort.findByEmail("p@example.com")).thenReturn(Optional.of(pending));

        authService.resendConfirmation("P@example.com", "ko");

        verify(emailConfirmationTokenPort).deleteByUserId(pending.getId());
        verify(emailConfirmationTokenPort).save(any(EmailConfirmationToken.class));
        verify(emailService).sendEmailConfirmation(eq(pending), anyString(), eq(24L), eq(Language.KO));
    }

    // --- google login: account policy for a verified identity ---

    @Test
    void googleLogin_mergeMatchesPasswordAccount_registeredInDifferentCase() {
        // The core exploit: a squatter's mixed-case password row must be found by
        // the (now normalized) merge lookup so the neutralize-password defense fires.
        when(googleIdTokenVerifier.verify("id-token")).thenReturn(googleIdentity("Victim@Gmail.com"));
        User squatted = User.builder().id(UUID.randomUUID()).email("victim@gmail.com")
                .passwordHash("SQUATTER").isAdmin(false).build();
        when(userPort.findByGoogleId("google-sub-123")).thenReturn(Optional.empty());
        when(userPort.findByEmail("victim@gmail.com")).thenReturn(Optional.of(squatted));
        when(userPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateToken(any(User.class))).thenReturn("a");
        when(jwtTokenProvider.generateRefreshToken(any(User.class))).thenReturn("r");

        authService.googleLogin("id-token");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userPort).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isNull();
        verify(refreshTokenPort).deleteByUserId(squatted.getId());
    }

    @Test
    void googleLogin_createsAccount_forUnknownVerifiedIdentity() {
        when(googleIdTokenVerifier.verify("id-token")).thenReturn(googleIdentity("g@example.com"));
        when(userPort.findByGoogleId("google-sub-123")).thenReturn(Optional.empty());
        when(userPort.findByEmail("g@example.com")).thenReturn(Optional.empty());
        when(userPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateToken(any(User.class))).thenReturn("a");
        when(jwtTokenProvider.generateRefreshToken(any(User.class))).thenReturn("r");

        AuthResponse response = authService.googleLogin("id-token");

        assertThat(response.getAccessToken()).isEqualTo("a");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userPort).save(captor.capture());
        assertThat(captor.getValue().getGoogleId()).isEqualTo("google-sub-123");
        assertThat(captor.getValue().getEmail()).isEqualTo("g@example.com");
        assertThat(captor.getValue().getFullName()).isEqualTo("Google User");
        assertThat(captor.getValue().getEmailVerified()).isTrue();
    }

    @Test
    void googleLogin_mergeIntoPasswordAccount_clearsPasswordAndRevokesSessions() {
        // Pre-account-hijack defense: a squatter registered the victim's email
        // with a password and never confirmed it. When the real owner signs in
        // with Google, the merge must neutralize the unproven password and revoke
        // all sessions so the squatter keeps no backdoor.
        when(googleIdTokenVerifier.verify("id-token")).thenReturn(googleIdentity("g@example.com"));
        User squatted = User.builder().id(UUID.randomUUID()).email("g@example.com")
                .passwordHash("SQUATTER-HASH").isAdmin(false).build();
        when(userPort.findByGoogleId("google-sub-123")).thenReturn(Optional.empty());
        when(userPort.findByEmail("g@example.com")).thenReturn(Optional.of(squatted));
        when(userPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateToken(any(User.class))).thenReturn("a");
        when(jwtTokenProvider.generateRefreshToken(any(User.class))).thenReturn("r");

        authService.googleLogin("id-token");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userPort).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isNull();
        assertThat(userCaptor.getValue().getGoogleId()).isEqualTo("google-sub-123");
        assertThat(userCaptor.getValue().getEmailVerified()).isTrue();
        verify(refreshTokenPort).deleteByUserId(squatted.getId());
        verify(emailConfirmationTokenPort).deleteByUserId(squatted.getId());
    }

    /** Stubs a Google login whose email lookup finds {@code existing}. */
    private void stubGoogleMergeInto(User existing) {
        when(googleIdTokenVerifier.verify("id-token")).thenReturn(googleIdentity(existing.getEmail()));
        when(userPort.findByGoogleId("google-sub-123")).thenReturn(Optional.empty());
        when(userPort.findByEmail(existing.getEmail())).thenReturn(Optional.of(existing));
        when(userPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateToken(any(User.class))).thenReturn("a");
        when(jwtTokenProvider.generateRefreshToken(any(User.class))).thenReturn("r");
    }

    @Test
    void googleLogin_mergeIntoLegacyAutoVerifiedAccount_stillClearsPasswordAndRevokesSessions() {
        // Pre-V32 register() auto-verified every address, so email_verified=true
        // proves nothing: with no email_confirmed_at the squatter defense must fire.
        User legacy = User.builder().id(UUID.randomUUID()).email("g@example.com")
                .passwordHash("LEGACY-HASH").emailVerified(true).isAdmin(false).build();
        stubGoogleMergeInto(legacy);

        authService.googleLogin("id-token");

        assertThat(legacy.getPasswordHash()).isNull();
        assertThat(legacy.getGoogleId()).isEqualTo("google-sub-123");
        verify(refreshTokenPort).deleteByUserId(legacy.getId());
        verify(emailConfirmationTokenPort).deleteByUserId(legacy.getId());
    }

    @Test
    void googleLogin_mergeIntoAccountConfirmedViaLink_keepsPasswordAndSessions() {
        // Proven inbox owner (opened the sign-up link) + Google proving the same
        // inbox: same person, so just link.
        User owner = User.builder().id(UUID.randomUUID()).email("g@example.com")
                .passwordHash("OWNER-HASH").emailVerified(false).isAdmin(false).build();
        EmailConfirmationToken link = EmailConfirmationToken.builder().id(UUID.randomUUID()).user(owner)
                .tokenHash(sha("raw")).expiresAt(LocalDateTime.now().plusHours(1)).build();
        when(emailConfirmationTokenPort.findByTokenHash(sha("raw"))).thenReturn(Optional.of(link));
        when(emailConfirmationTokenPort.markUsed(eq(link.getId()), any(LocalDateTime.class))).thenReturn(1);
        stubGoogleMergeInto(owner);

        authService.confirmEmail("raw");
        assertThat(owner.getEmailConfirmedAt()).isNotNull();
        authService.googleLogin("id-token");

        assertThat(owner.getPasswordHash()).isEqualTo("OWNER-HASH");
        assertThat(owner.getGoogleId()).isEqualTo("google-sub-123");
        verify(refreshTokenPort, never()).deleteByUserId(any());
        verify(emailConfirmationTokenPort, never()).deleteByUserId(any());
    }

    @Test
    void googleLogin_mergeIntoAccountConfirmedViaPasswordReset_keepsPasswordAndSessions() {
        // A completed password reset also proves the inbox — including for a
        // legacy auto-verified account, which is how such a user opts back in.
        User owner = User.builder().id(UUID.randomUUID()).email("g@example.com")
                .passwordHash("OLD-HASH").emailVerified(true).isAdmin(false).build();
        PasswordResetToken reset = PasswordResetToken.builder().user(owner)
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
        when(passwordResetTokenPort.findByTokenHash(anyString())).thenReturn(Optional.of(reset));
        when(passwordEncoder.encode("Newpass123!")).thenReturn("RESET-HASH");
        stubGoogleMergeInto(owner);

        authService.resetPassword("ok", "Newpass123!");
        assertThat(owner.getEmailConfirmedAt()).isNotNull();
        // The reset itself revokes sessions; the merge must not do it again.
        clearInvocations(refreshTokenPort);
        authService.googleLogin("id-token");

        assertThat(owner.getPasswordHash()).isEqualTo("RESET-HASH");
        assertThat(owner.getGoogleId()).isEqualTo("google-sub-123");
        verify(refreshTokenPort, never()).deleteByUserId(any());
        verify(emailConfirmationTokenPort, never()).deleteByUserId(any());
    }

    @Test
    void googleLogin_relink_doesNotTouchPassword_whenAlreadyLinked() {
        // An account already bound to this googleId is a normal repeat login —
        // no merge is happening, so nothing about the password may change.
        when(googleIdTokenVerifier.verify("id-token")).thenReturn(googleIdentity("g@example.com"));
        User linked = User.builder().id(UUID.randomUUID()).email("g@example.com")
                .googleId("google-sub-123").passwordHash("THEIR-OWN-HASH").isAdmin(false).build();
        when(userPort.findByGoogleId("google-sub-123")).thenReturn(Optional.of(linked));
        when(userPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateToken(any(User.class))).thenReturn("a");
        when(jwtTokenProvider.generateRefreshToken(any(User.class))).thenReturn("r");

        authService.googleLogin("id-token");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userPort).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("THEIR-OWN-HASH");
        verify(refreshTokenPort, never()).deleteByUserId(any());
    }

    @Test
    void googleLogin_rejects_whenGoogleHasNotVerifiedTheEmail() {
        // An unverified Google email must never create or merge an account — that
        // would let anyone claim an address they don't own.
        when(googleIdTokenVerifier.verify("id-token"))
                .thenReturn(new GoogleIdentity("google-sub-123", "g@example.com", "Google User", false));

        assertThatThrownBy(() -> authService.googleLogin("id-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Google account email is not verified");
        verify(userPort, never()).save(any());
        verify(refreshTokenPort, never()).save(any());
    }

    @Test
    void googleLogin_rejects_whenTokenVerificationFails() {
        when(googleIdTokenVerifier.verify("id-token"))
                .thenThrow(new UnauthorizedException("Invalid Google token"));

        assertThatThrownBy(() -> authService.googleLogin("id-token"))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(userPort);
        verify(refreshTokenPort, never()).save(any());
    }

    // --- login ---

    @Test
    void login_startsEmailVerification_onSuccessfulAuth() {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@example.com");
        req.setPassword("Pa$$word123");
        User user = User.builder().id(UUID.randomUUID()).email("user@example.com")
                .fullName("User").isAdmin(false).emailVerified(true).build();
        when(userPort.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        LoginResponse response = authService.login(req);

        assertThat(response.getStatus()).isEqualTo("VERIFICATION_REQUIRED");
        assertThat(response.getChallengeId()).isNotBlank();
        assertThat(response.getEmail()).isEqualTo("user@example.com");
        assertThat(response.getAttemptsRemaining()).isEqualTo(5);
        assertThat(response.getAuth()).isNull();
        verify(authenticationManager).authenticate(any());
        verify(emailVerificationCodePort).deleteByUserId(user.getId());
        verify(emailVerificationCodePort).save(any(EmailVerificationCode.class));
        verify(emailService).sendLoginVerificationEmail(eq(user), anyString(), eq(10L));
        // No session issued yet — that only happens after the code is verified.
        verify(refreshTokenPort, never()).save(any());
    }

    @Test
    void login_returnsTokensImmediately_whenVerificationDisabled() {
        ReflectionTestUtils.setField(challengeService, "enabled", false);
        LoginRequest req = new LoginRequest();
        req.setEmail("user@example.com");
        req.setPassword("Pa$$word123");
        User user = User.builder().id(UUID.randomUUID()).email("user@example.com")
                .fullName("User").isAdmin(false).emailVerified(true).build();
        when(userPort.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken(user)).thenReturn("a");
        when(jwtTokenProvider.generateRefreshToken(user)).thenReturn("r");

        LoginResponse response = authService.login(req);

        assertThat(response.getStatus()).isEqualTo("AUTHENTICATED");
        assertThat(response.getAuth().getAccessToken()).isEqualTo("a");
        assertThat(response.getAuth().getRefreshToken()).isEqualTo("r");
        verify(refreshTokenPort).save(any(RefreshToken.class));
        verify(emailVerificationCodePort, never()).save(any());
    }

    @Test
    void login_propagates_authenticationFailure() {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@example.com");
        req.setPassword("wrong");
        doThrow(new org.springframework.security.authentication.BadCredentialsException("nope"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);

        verify(refreshTokenPort, never()).save(any());
    }

    @Test
    void login_unconfirmedAccount_withTheRightPassword_isEmailNotConfirmed_andSendsNoCode() {
        LoginRequest req = new LoginRequest();
        req.setEmail("pending@example.com");
        req.setPassword("Pa$$word123");
        User pending = User.builder().id(UUID.randomUUID()).email("pending@example.com")
                .emailVerified(false).build();
        when(userPort.findByEmail("pending@example.com")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> authService.login(req)).isInstanceOf(EmailNotConfirmedException.class);

        verify(authenticationManager).authenticate(any());
        verifyNoInteractions(emailVerificationCodePort, emailService);
        verify(refreshTokenPort, never()).save(any());
    }

    @Test
    void login_unconfirmedAccount_withAWrongPassword_isTheGenericBadCredentials() {
        // The confirmation state is only revealed once the password checked out.
        LoginRequest req = new LoginRequest();
        req.setEmail("pending@example.com");
        req.setPassword("wrong");
        doThrow(new org.springframework.security.authentication.BadCredentialsException("nope"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        verifyNoInteractions(userPort);
    }

    // --- verifyCode: challenge outcome → response contract + session issuance ---

    @Test
    void verifyCode_issuesTokens_whenCodeMatches() {
        User user = User.builder().id(UUID.randomUUID()).email("u@e.com").fullName("U").isAdmin(false).build();
        EmailVerificationCode vc = EmailVerificationCode.builder()
                .id(UUID.randomUUID()).user(user)
                .challengeHash("ch").codeHash(sha("123456"))
                .attemptsRemaining(5)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        when(emailVerificationCodePort.findByChallengeHash(anyString())).thenReturn(Optional.of(vc));
        when(jwtTokenProvider.generateToken(user)).thenReturn("a");
        when(jwtTokenProvider.generateRefreshToken(user)).thenReturn("r");

        VerifyCodeRequest req = new VerifyCodeRequest();
        req.setChallengeId("raw-challenge");
        req.setCode("123456");

        VerifyCodeResponse response = authService.verifyCode(req);

        assertThat(response.getStatus()).isEqualTo("VERIFIED");
        assertThat(response.getMessage()).isEqualTo("Verified.");
        assertThat(response.getAttemptsRemaining()).isEqualTo(5);
        assertThat(response.isResetAvailable()).isFalse();
        assertThat(response.getAuth().getAccessToken()).isEqualTo("a");
        verify(emailVerificationCodePort).deleteByUserId(user.getId());
        verify(refreshTokenPort).save(any(RefreshToken.class));
    }

    @Test
    void verifyCode_decrementsAttempts_onWrongCode() {
        User user = User.builder().id(UUID.randomUUID()).email("u@e.com").build();
        EmailVerificationCode vc = EmailVerificationCode.builder()
                .id(UUID.randomUUID()).user(user)
                .challengeHash("ch").codeHash(sha("111111"))
                .attemptsRemaining(5)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        when(emailVerificationCodePort.findByChallengeHash(anyString())).thenReturn(Optional.of(vc));
        when(emailVerificationCodePort.decrementAttempts(vc.getId())).thenReturn(4);

        VerifyCodeRequest req = new VerifyCodeRequest();
        req.setChallengeId("raw-challenge");
        req.setCode("000000");

        VerifyCodeResponse response = authService.verifyCode(req);

        assertThat(response.getStatus()).isEqualTo("INVALID_CODE");
        assertThat(response.getAttemptsRemaining()).isEqualTo(4);
        assertThat(response.isResetAvailable()).isFalse();
        assertThat(response.getMessage()).isEqualTo("That code isn't right. 4 attempts left.");
        assertThat(response.getAuth()).isNull();
        verify(emailVerificationCodePort).decrementAttempts(vc.getId());
        verify(emailVerificationCodePort, never()).markConsumed(any());
        verify(refreshTokenPort, never()).save(any());
    }

    @Test
    void verifyCode_locksAndOffersReset_onLastWrongAttempt() {
        User user = User.builder().id(UUID.randomUUID()).email("u@e.com").build();
        EmailVerificationCode vc = EmailVerificationCode.builder()
                .id(UUID.randomUUID()).user(user)
                .challengeHash("ch").codeHash(sha("111111"))
                .attemptsRemaining(1)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        when(emailVerificationCodePort.findByChallengeHash(anyString())).thenReturn(Optional.of(vc));
        when(emailVerificationCodePort.decrementAttempts(vc.getId())).thenReturn(0);

        VerifyCodeRequest req = new VerifyCodeRequest();
        req.setChallengeId("raw-challenge");
        req.setCode("999999");

        VerifyCodeResponse response = authService.verifyCode(req);

        assertThat(response.getStatus()).isEqualTo("LOCKED");
        assertThat(response.getAttemptsRemaining()).isEqualTo(0);
        assertThat(response.isResetAvailable()).isTrue();
        assertThat(response.getMessage())
                .isEqualTo("Too many incorrect codes. For your security, reset your password to sign in.");
        verify(emailVerificationCodePort).markConsumed(vc.getId());
        verify(refreshTokenPort, never()).save(any());
    }

    @Test
    void verifyCode_returnsExpired_forUnknownChallenge() {
        when(emailVerificationCodePort.findByChallengeHash(anyString())).thenReturn(Optional.empty());

        VerifyCodeRequest req = new VerifyCodeRequest();
        req.setChallengeId("gone");
        req.setCode("123456");

        VerifyCodeResponse response = authService.verifyCode(req);

        assertThat(response.getStatus()).isEqualTo("EXPIRED");
        assertThat(response.getAttemptsRemaining()).isEqualTo(0);
        assertThat(response.isResetAvailable()).isFalse();
        assertThat(response.getMessage())
                .isEqualTo("This verification code has expired. Please sign in again.");
        verify(refreshTokenPort, never()).save(any());
    }

    // --- refreshToken ---

    @Test
    void refreshToken_rejectsUnknownToken() {
        when(refreshTokenPort.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken("foo"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshToken_rejectsExpiredToken_andDeletes() {
        User user = User.builder().id(UUID.randomUUID()).email("u@e.com").build();
        RefreshToken expired = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
        when(refreshTokenPort.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refreshToken("foo"))
                .isInstanceOf(UnauthorizedException.class);
        verify(refreshTokenPort).delete(expired);
    }

    @Test
    void refreshToken_rotatesAndIssuesFresh() {
        User user = User.builder().id(UUID.randomUUID()).email("u@e.com").fullName("U").isAdmin(false).build();
        RefreshToken existing = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        when(refreshTokenPort.findByTokenHash(anyString())).thenReturn(Optional.of(existing));
        when(refreshTokenPort.markRotated(eq(existing.getId()), any(LocalDateTime.class))).thenReturn(1);
        when(jwtTokenProvider.generateToken(user)).thenReturn("a2");
        when(jwtTokenProvider.generateRefreshToken(user)).thenReturn("r2");

        AuthResponse response = authService.refreshToken("foo");

        assertThat(response.getAccessToken()).isEqualTo("a2");
        assertThat(response.getRefreshToken()).isEqualTo("r2");
        // The presented row is kept (stamped rotated_at) for reuse detection, not deleted.
        verify(refreshTokenPort, never()).delete(any());
        verify(refreshTokenPort, never()).deleteByUserId(any());
        verify(refreshTokenPort).save(any(RefreshToken.class));
        verifyNoInteractions(auditLog);
    }

    private RefreshToken rotatedToken(User user, long secondsAgo) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .rotatedAt(LocalDateTime.now().minusSeconds(secondsAgo))
                .build();
    }

    @Test
    void refreshToken_reusedAfterGraceWindow_revokesEverySession_andAudits() {
        User user = User.builder().id(UUID.randomUUID()).email("u@e.com").build();
        RefreshToken replayed = rotatedToken(user, 120);
        when(refreshTokenPort.findByTokenHash(anyString())).thenReturn(Optional.of(replayed));

        assertThatThrownBy(() -> authService.refreshToken("stolen"))
                .isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenPort).deleteByUserId(user.getId());
        verify(auditLog).record(eq(AuthService.AUDIT_REFRESH_TOKEN_REUSE), eq("USER"),
                eq(user.getId().toString()), anyMap());
        verify(refreshTokenPort, never()).markRotated(any(), any());
        verify(refreshTokenPort, never()).save(any());
    }

    @Test
    void refreshToken_reusedWithinGraceWindow_rejectsWithoutTouchingOtherSessions() {
        User user = User.builder().id(UUID.randomUUID()).email("u@e.com").build();
        RefreshToken replayed = rotatedToken(user, 2);
        when(refreshTokenPort.findByTokenHash(anyString())).thenReturn(Optional.of(replayed));

        assertThatThrownBy(() -> authService.refreshToken("raced"))
                .isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenPort, never()).deleteByUserId(any());
        verify(refreshTokenPort, never()).delete(any());
        verify(refreshTokenPort, never()).save(any());
        verifyNoInteractions(auditLog);
    }

    @Test
    void refreshToken_lostConcurrentRotation_rejectsWithoutTouchingOtherSessions() {
        User user = User.builder().id(UUID.randomUUID()).email("u@e.com").build();
        RefreshToken live = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(refreshTokenPort.findByTokenHash(anyString())).thenReturn(Optional.of(live));
        // The other tab's UPDATE ... WHERE rotated_at IS NULL committed first.
        when(refreshTokenPort.markRotated(eq(live.getId()), any(LocalDateTime.class))).thenReturn(0);

        assertThatThrownBy(() -> authService.refreshToken("raced"))
                .isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenPort, never()).deleteByUserId(any());
        verify(refreshTokenPort, never()).save(any());
        verifyNoInteractions(auditLog, jwtTokenProvider);
    }

    @Test
    void refreshToken_expiredRotatedToken_isNotTreatedAsReuse() {
        User user = User.builder().id(UUID.randomUUID()).email("u@e.com").build();
        RefreshToken stale = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .rotatedAt(LocalDateTime.now().minusHours(3))
                .build();
        when(refreshTokenPort.findByTokenHash(anyString())).thenReturn(Optional.of(stale));

        assertThatThrownBy(() -> authService.refreshToken("old"))
                .isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenPort).delete(stale);
        verify(refreshTokenPort, never()).deleteByUserId(any());
        verifyNoInteractions(auditLog);
    }

    @Test
    void refreshToken_loggedOutToken_isNotTreatedAsReuse() {
        // Logout deletes the row, so a replay of a logged-out token is simply unknown.
        RefreshToken session = RefreshToken.builder().id(UUID.randomUUID()).build();
        when(refreshTokenPort.findByTokenHash(anyString()))
                .thenReturn(Optional.of(session))
                .thenReturn(Optional.empty());

        authService.logout("token");
        assertThatThrownBy(() -> authService.refreshToken("token"))
                .isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenPort).delete(session);
        verify(refreshTokenPort, never()).deleteByUserId(any());
        verifyNoInteractions(auditLog);
    }

    // --- logout ---

    @Test
    void logout_isNoOp_forNullOrBlank() {
        authService.logout(null);
        authService.logout("  ");
        verifyNoInteractions(refreshTokenPort);
    }

    @Test
    void logout_deletesKnownToken() {
        RefreshToken existing = RefreshToken.builder().id(UUID.randomUUID()).build();
        when(refreshTokenPort.findByTokenHash(anyString())).thenReturn(Optional.of(existing));

        authService.logout("token");

        verify(refreshTokenPort).delete(existing);
    }

    // --- requestPasswordReset ---

    @Test
    void requestPasswordReset_silentlyIgnoresUnknownEmail() {
        when(userPort.findByEmail(anyString())).thenReturn(Optional.empty());

        authService.requestPasswordReset("ghost@example.com");

        verify(passwordResetTokenPort, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), anyString());
    }

    @Test
    void requestPasswordReset_savesTokenAndEmails() {
        User user = User.builder().id(UUID.randomUUID()).email("real@example.com").build();
        when(userPort.findByEmail("real@example.com")).thenReturn(Optional.of(user));

        authService.requestPasswordReset("real@example.com");

        verify(passwordResetTokenPort).deleteByUserId(user.getId());
        verify(passwordResetTokenPort).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(eq(user), anyString());
    }

    // --- resetPassword ---

    @Test
    void resetPassword_rejectsUnknownToken() {
        when(passwordResetTokenPort.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("bad", "Pa$$word123"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void resetPassword_rejectsAlreadyUsedToken() {
        User user = User.builder().id(UUID.randomUUID()).build();
        PasswordResetToken used = PasswordResetToken.builder()
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .usedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(passwordResetTokenPort.findByTokenHash(anyString())).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> authService.resetPassword("ok", "Pa$$word123"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void resetPassword_updatesPasswordHash_andMarksTokenUsed() {
        User user = User.builder().id(UUID.randomUUID()).email("r@e.com").emailVerified(false).loginCodeFailures(10).build();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(passwordResetTokenPort.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("Newpass123!")).thenReturn("NEW_HASH");

        authService.resetPassword("ok", "Newpass123!");

        assertThat(user.getPasswordHash()).isEqualTo("NEW_HASH");
        // A password reset is the recovery path out of a 2FA lockout.
        verify(userPort).resetLoginCodeFailures(user.getId());
        assertThat(user.getEmailVerified()).isTrue();
        // Opening the reset link proves the inbox (trusted by the Google merge).
        assertThat(user.getEmailConfirmedAt()).isNotNull();
        assertThat(token.getUsedAt()).isNotNull();
        verify(userPort).save(user);
        verify(passwordResetTokenPort).save(token);
        verify(refreshTokenPort).deleteByUserId(user.getId());
    }
}
