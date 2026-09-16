package com.curio.auth.service;

import com.curio.auth.dto.AuthResponse;
import com.curio.auth.dto.LoginRequest;
import com.curio.auth.dto.LoginResponse;
import com.curio.auth.dto.RegisterRequest;
import com.curio.auth.dto.ResendCodeRequest;
import com.curio.auth.dto.ResendCodeResponse;
import com.curio.auth.dto.VerifyCodeRequest;
import com.curio.auth.dto.VerifyCodeResponse;
import com.curio.auth.entity.EmailVerificationCode;
import com.curio.auth.entity.PasswordResetToken;
import com.curio.auth.entity.RefreshToken;
import com.curio.auth.port.out.EmailVerificationCodePort;
import com.curio.auth.port.out.PasswordResetTokenPort;
import com.curio.auth.port.out.RefreshTokenPort;
import com.curio.shared.email.EmailService;
import com.curio.shared.exception.UnauthorizedException;
import com.curio.shared.security.JwtTokenProvider;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserPort userPort;
    @Mock private RefreshTokenPort refreshTokenPort;
    @Mock private PasswordResetTokenPort passwordResetTokenPort;
    @Mock private EmailVerificationCodePort emailVerificationCodePort;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private EmailService emailService;
    @Mock private RestTemplateBuilder restTemplateBuilder;
    @Mock private RestTemplate restTemplate;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userPort, refreshTokenPort, passwordResetTokenPort,
                emailVerificationCodePort, passwordEncoder, jwtTokenProvider, authenticationManager,
                emailService, restTemplateBuilder);
        // @Value fields aren't injected in a plain Mockito unit test — set them by hand.
        ReflectionTestUtils.setField(authService, "emailVerificationEnabled", true);
        ReflectionTestUtils.setField(authService, "maxVerificationAttempts", 5);
        ReflectionTestUtils.setField(authService, "verificationCodeLength", 6);
        ReflectionTestUtils.setField(authService, "codeTtlMinutes", 10L);
    }

    /** Mirrors AuthService.hashToken so tests can stage a known code hash. */
    private static String sha(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Wires the mock RestTemplate (normally built in @PostConstruct) and a configured client id. */
    private void configureGoogle(String clientId) {
        ReflectionTestUtils.setField(authService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(authService, "googleClientId", clientId);
    }

    private Map<String, Object> googleTokenInfo(String aud, String iss) {
        Map<String, Object> info = new HashMap<>();
        info.put("sub", "google-sub-123");
        info.put("email", "g@example.com");
        info.put("name", "Google User");
        info.put("email_verified", "true");
        info.put("aud", aud);
        info.put("iss", iss);
        return info;
    }

    // --- register ---

    @Test
    void register_persistsUser_andIssuesTokens() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@example.com");
        req.setPassword("Pa$$word123");
        req.setFullName("New User");

        when(userPort.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Pa$$word123")).thenReturn("HASHED");
        User saved = User.builder().id(UUID.randomUUID()).email("new@example.com")
                .fullName("New User").isAdmin(false).build();
        when(userPort.save(any(User.class))).thenReturn(saved);
        when(jwtTokenProvider.generateToken(saved)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(saved)).thenReturn("refresh-token");

        AuthResponse response = authService.register(req);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getEmail()).isEqualTo("new@example.com");
        assertThat(response.getIsAdmin()).isFalse();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userPort).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("HASHED");
        assertThat(userCaptor.getValue().getEmailVerified()).isTrue();
        verify(refreshTokenPort).save(any(RefreshToken.class));
    }

    @Test
    void register_rejects_duplicateEmail() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("dup@example.com");
        req.setPassword("Pa$$word123");
        req.setFullName("Dup");
        when(userPort.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userPort, never()).save(any());
        verify(refreshTokenPort, never()).save(any());
    }

    @Test
    void register_normalizesEmail_soCaseVariantCollidesAndIsStoredLowercase() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("  Victim@Gmail.COM ");
        req.setPassword("Pa$$word123");
        req.setFullName("V");
        // Existence check must run against the normalized form — a case variant
        // of an existing address must NOT slip through as a fresh account.
        when(userPort.existsByEmail("victim@gmail.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
        when(userPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateToken(any(User.class))).thenReturn("a");
        when(jwtTokenProvider.generateRefreshToken(any(User.class))).thenReturn("r");

        authService.register(req);

        verify(userPort).existsByEmail("victim@gmail.com");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userPort).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("victim@gmail.com");
    }

    @Test
    void googleLogin_mergeMatchesPasswordAccount_registeredInDifferentCase() {
        // The core exploit: a squatter's mixed-case password row must be found by
        // the (now normalized) merge lookup so the neutralize-password defense fires.
        configureGoogle("my-client-id.apps.googleusercontent.com");
        Map<String, Object> info = googleTokenInfo("my-client-id.apps.googleusercontent.com", "https://accounts.google.com");
        info.put("email", "Victim@Gmail.com");
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(info);
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

    // --- google login: audience / issuer validation ---

    @Test
    void googleLogin_succeeds_whenAudienceAndIssuerMatch() {
        configureGoogle("my-client-id.apps.googleusercontent.com");
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(googleTokenInfo("my-client-id.apps.googleusercontent.com", "https://accounts.google.com"));
        when(userPort.findByGoogleId("google-sub-123")).thenReturn(Optional.empty());
        when(userPort.findByEmail("g@example.com")).thenReturn(Optional.empty());
        when(userPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateToken(any(User.class))).thenReturn("a");
        when(jwtTokenProvider.generateRefreshToken(any(User.class))).thenReturn("r");

        AuthResponse response = authService.googleLogin("id-token");

        assertThat(response.getAccessToken()).isEqualTo("a");
        verify(userPort).save(any(User.class));
    }

    @Test
    void googleLogin_mergeIntoPasswordAccount_clearsPasswordAndRevokesSessions() {
        // Pre-account-hijack defense: a squatter registered the victim's email
        // with a password (register auto-verifies). When the real owner signs in
        // with Google, the merge must neutralize the unproven password and revoke
        // all sessions so the squatter keeps no backdoor.
        configureGoogle("my-client-id.apps.googleusercontent.com");
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(googleTokenInfo("my-client-id.apps.googleusercontent.com", "https://accounts.google.com"));
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
        verify(refreshTokenPort).deleteByUserId(squatted.getId());
    }

    @Test
    void googleLogin_relink_doesNotTouchPassword_whenAlreadyLinked() {
        // An account already bound to this googleId is a normal repeat login —
        // no merge is happening, so nothing about the password may change.
        configureGoogle("my-client-id.apps.googleusercontent.com");
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(googleTokenInfo("my-client-id.apps.googleusercontent.com", "https://accounts.google.com"));
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
    void googleLogin_rejects_whenAudienceMismatch() {
        configureGoogle("my-client-id.apps.googleusercontent.com");
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(googleTokenInfo("attacker-client-id", "https://accounts.google.com"));

        assertThatThrownBy(() -> authService.googleLogin("id-token"))
                .isInstanceOf(UnauthorizedException.class);
        verify(userPort, never()).save(any());
    }

    @Test
    void googleLogin_rejects_whenIssuerInvalid() {
        configureGoogle("my-client-id.apps.googleusercontent.com");
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(googleTokenInfo("my-client-id.apps.googleusercontent.com", "https://evil.example.com"));

        assertThatThrownBy(() -> authService.googleLogin("id-token"))
                .isInstanceOf(UnauthorizedException.class);
        verify(userPort, never()).save(any());
    }

    @Test
    void googleLogin_rejects_whenClientIdNotConfigured() {
        configureGoogle("not-configured");
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(googleTokenInfo("anything", "https://accounts.google.com"));

        assertThatThrownBy(() -> authService.googleLogin("id-token"))
                .isInstanceOf(UnauthorizedException.class);
        verify(userPort, never()).save(any());
    }

    // --- login ---

    @Test
    void login_startsEmailVerification_onSuccessfulAuth() {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@example.com");
        req.setPassword("Pa$$word123");
        User user = User.builder().id(UUID.randomUUID()).email("user@example.com")
                .fullName("User").isAdmin(false).build();
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
        ReflectionTestUtils.setField(authService, "emailVerificationEnabled", false);
        LoginRequest req = new LoginRequest();
        req.setEmail("user@example.com");
        req.setPassword("Pa$$word123");
        User user = User.builder().id(UUID.randomUUID()).email("user@example.com")
                .fullName("User").isAdmin(false).build();
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

    // --- verifyCode ---

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
        verify(refreshTokenPort, never()).save(any());
    }

    // --- resendCode ---

    @Test
    void resendCode_emailsFreshCode_andKeepsAttempts() {
        User user = User.builder().id(UUID.randomUUID()).email("u@e.com").build();
        EmailVerificationCode vc = EmailVerificationCode.builder()
                .id(UUID.randomUUID()).user(user)
                .challengeHash("ch").codeHash(sha("111111"))
                .attemptsRemaining(3)
                .expiresAt(LocalDateTime.now().plusMinutes(2))
                .build();
        when(emailVerificationCodePort.findByChallengeHash(anyString())).thenReturn(Optional.of(vc));

        ResendCodeRequest req = new ResendCodeRequest();
        req.setChallengeId("raw-challenge");

        ResendCodeResponse response = authService.resendCode(req);

        assertThat(response.getStatus()).isEqualTo("SENT");
        assertThat(response.getAttemptsRemaining()).isEqualTo(3);
        verify(emailVerificationCodePort).save(vc);
        verify(emailService).sendLoginVerificationEmail(eq(user), anyString(), eq(10L));
    }

    @Test
    void resendCode_returnsExpired_forUnknownChallenge() {
        when(emailVerificationCodePort.findByChallengeHash(anyString())).thenReturn(Optional.empty());

        ResendCodeRequest req = new ResendCodeRequest();
        req.setChallengeId("gone");

        ResendCodeResponse response = authService.resendCode(req);

        assertThat(response.getStatus()).isEqualTo("EXPIRED");
        verify(emailService, never()).sendLoginVerificationEmail(any(), anyString(), anyLong());
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
        when(jwtTokenProvider.generateToken(user)).thenReturn("a2");
        when(jwtTokenProvider.generateRefreshToken(user)).thenReturn("r2");

        AuthResponse response = authService.refreshToken("foo");

        assertThat(response.getAccessToken()).isEqualTo("a2");
        assertThat(response.getRefreshToken()).isEqualTo("r2");
        verify(refreshTokenPort).delete(existing);
        verify(refreshTokenPort).save(any(RefreshToken.class));
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
        User user = User.builder().id(UUID.randomUUID()).email("r@e.com").emailVerified(false).build();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(passwordResetTokenPort.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("Newpass123!")).thenReturn("NEW_HASH");

        authService.resetPassword("ok", "Newpass123!");

        assertThat(user.getPasswordHash()).isEqualTo("NEW_HASH");
        assertThat(user.getEmailVerified()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        verify(userPort).save(user);
        verify(passwordResetTokenPort).save(token);
        verify(refreshTokenPort).deleteByUserId(user.getId());
    }
}
