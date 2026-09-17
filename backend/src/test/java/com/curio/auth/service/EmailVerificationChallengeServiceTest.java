package com.curio.auth.service;

import com.curio.auth.dto.LoginResponse;
import com.curio.auth.dto.ResendCodeResponse;
import com.curio.auth.entity.EmailVerificationCode;
import com.curio.auth.port.out.EmailVerificationCodePort;
import com.curio.auth.service.EmailVerificationChallengeService.Verification;
import com.curio.shared.port.in.EmailUseCase;
import com.curio.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationChallengeServiceTest {

    @Mock private EmailVerificationCodePort emailVerificationCodePort;
    @Mock private EmailUseCase emailService;

    private EmailVerificationChallengeService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationChallengeService(emailVerificationCodePort, emailService);
        // @Value fields aren't injected in a plain Mockito unit test — set them by hand.
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        ReflectionTestUtils.setField(service, "codeLength", 6);
        ReflectionTestUtils.setField(service, "codeTtlMinutes", 10L);
    }

    private static User user() {
        return User.builder().id(UUID.randomUUID()).email("u@e.com").fullName("U").isAdmin(false).build();
    }

    private static EmailVerificationCode challenge(User user, String code, int attemptsRemaining) {
        return EmailVerificationCode.builder()
                .id(UUID.randomUUID()).user(user)
                .challengeHash("ch").codeHash(TokenHasher.hash(code))
                .attemptsRemaining(attemptsRemaining)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
    }

    // --- enabled ---

    @Test
    void isEnabled_reflectsConfiguration() {
        assertThat(service.isEnabled()).isTrue();
        ReflectionTestUtils.setField(service, "enabled", false);
        assertThat(service.isEnabled()).isFalse();
    }

    // --- start ---

    @Test
    void start_persistsHashedChallenge_andEmailsPlaintextCode() {
        User user = user();

        LoginResponse response = service.start(user);

        assertThat(response.getStatus()).isEqualTo("VERIFICATION_REQUIRED");
        assertThat(response.getChallengeId()).isNotBlank();
        assertThat(response.getEmail()).isEqualTo("u@e.com");
        assertThat(response.getAttemptsRemaining()).isEqualTo(5);
        assertThat(response.getExpiresInSeconds()).isEqualTo(600L);
        assertThat(response.getAuth()).isNull();

        // Any earlier challenge for this user is invalidated first.
        verify(emailVerificationCodePort).deleteByUserId(user.getId());

        ArgumentCaptor<String> emailedCode = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendLoginVerificationEmail(eq(user), emailedCode.capture(), eq(10L));
        assertThat(emailedCode.getValue()).matches("\\d{6}");

        ArgumentCaptor<EmailVerificationCode> saved = ArgumentCaptor.forClass(EmailVerificationCode.class);
        verify(emailVerificationCodePort).save(saved.capture());
        EmailVerificationCode row = saved.getValue();
        // Only digests reach the database: the challenge id handed to the client
        // and the code emailed to the user must both hash to what was stored.
        assertThat(row.getChallengeHash()).isEqualTo(TokenHasher.hash(response.getChallengeId()));
        assertThat(row.getCodeHash()).isEqualTo(TokenHasher.hash(emailedCode.getValue()));
        assertThat(row.getCodeHash()).isNotEqualTo(emailedCode.getValue());
        assertThat(row.getAttemptsRemaining()).isEqualTo(5);
        assertThat(row.getExpiresAt()).isCloseTo(LocalDateTime.now().plusMinutes(10), within(5, ChronoUnit.SECONDS));
        assertThat(row.getUser()).isSameAs(user);
    }

    @Test
    void start_stillIssuesChallenge_whenEmailDeliveryFails() {
        // Delivery is best-effort: the challenge must stand so the user can hit
        // resend rather than being locked out of sign-in by a mail outage.
        doThrow(new RuntimeException("resend 403")).when(emailService)
                .sendLoginVerificationEmail(any(), anyString(), anyLong());

        LoginResponse response = service.start(user());

        assertThat(response.getStatus()).isEqualTo("VERIFICATION_REQUIRED");
        verify(emailVerificationCodePort).save(any(EmailVerificationCode.class));
    }

    // --- verify ---

    @Test
    void verify_returnsUser_andBurnsChallenge_whenCodeMatches() {
        User user = user();
        EmailVerificationCode vc = challenge(user, "123456", 5);
        when(emailVerificationCodePort.findByChallengeHash(TokenHasher.hash("raw-challenge")))
                .thenReturn(Optional.of(vc));

        Verification outcome = service.verify("raw-challenge", "123456");

        assertThat(outcome.status()).isEqualTo(Verification.Status.VERIFIED);
        assertThat(outcome.verified()).isTrue();
        assertThat(outcome.user()).isSameAs(user);
        assertThat(outcome.attemptsRemaining()).isEqualTo(5);
        assertThat(outcome.resetAvailable()).isFalse();
        assertThat(outcome.message()).isEqualTo("Verified.");
        // The code can't be replayed: every challenge for the user is removed.
        verify(emailVerificationCodePort).deleteByUserId(user.getId());
        verify(emailVerificationCodePort, never()).decrementAttempts(any());
    }

    @Test
    void verify_decrementsAttempts_onWrongCode() {
        EmailVerificationCode vc = challenge(user(), "111111", 5);
        when(emailVerificationCodePort.findByChallengeHash(anyString())).thenReturn(Optional.of(vc));
        when(emailVerificationCodePort.decrementAttempts(vc.getId())).thenReturn(4);

        Verification outcome = service.verify("raw-challenge", "000000");

        assertThat(outcome.status()).isEqualTo(Verification.Status.INVALID_CODE);
        assertThat(outcome.verified()).isFalse();
        assertThat(outcome.user()).isNull();
        assertThat(outcome.attemptsRemaining()).isEqualTo(4);
        assertThat(outcome.resetAvailable()).isFalse();
        assertThat(outcome.message()).isEqualTo("That code isn't right. 4 attempts left.");
        verify(emailVerificationCodePort, never()).markConsumed(any());
        verify(emailVerificationCodePort, never()).deleteByUserId(any());
    }

    @Test
    void verify_usesSingularCopy_forOneAttemptLeft() {
        EmailVerificationCode vc = challenge(user(), "111111", 2);
        when(emailVerificationCodePort.findByChallengeHash(anyString())).thenReturn(Optional.of(vc));
        when(emailVerificationCodePort.decrementAttempts(vc.getId())).thenReturn(1);

        Verification outcome = service.verify("raw-challenge", "000000");

        assertThat(outcome.message()).isEqualTo("That code isn't right. 1 attempt left.");
    }

    @Test
    void verify_locksAndOffersReset_onLastWrongAttempt() {
        EmailVerificationCode vc = challenge(user(), "111111", 1);
        when(emailVerificationCodePort.findByChallengeHash(anyString())).thenReturn(Optional.of(vc));
        when(emailVerificationCodePort.decrementAttempts(vc.getId())).thenReturn(0);

        Verification outcome = service.verify("raw-challenge", "999999");

        assertThat(outcome.status()).isEqualTo(Verification.Status.LOCKED);
        assertThat(outcome.attemptsRemaining()).isEqualTo(0);
        assertThat(outcome.resetAvailable()).isTrue();
        assertThat(outcome.user()).isNull();
        assertThat(outcome.message())
                .isEqualTo("Too many incorrect codes. For your security, reset your password to sign in.");
        verify(emailVerificationCodePort).markConsumed(vc.getId());
    }

    @Test
    void verify_returnsExpired_forUnknownChallenge() {
        when(emailVerificationCodePort.findByChallengeHash(anyString())).thenReturn(Optional.empty());

        Verification outcome = service.verify("gone", "123456");

        assertThat(outcome.status()).isEqualTo(Verification.Status.EXPIRED);
        assertThat(outcome.attemptsRemaining()).isEqualTo(0);
        assertThat(outcome.resetAvailable()).isFalse();
        assertThat(outcome.user()).isNull();
        assertThat(outcome.message()).isEqualTo("This verification code has expired. Please sign in again.");
        verify(emailVerificationCodePort, never()).delete(any());
    }

    @Test
    void verify_deletesStaleRow_whenChallengeExpired() {
        EmailVerificationCode stale = challenge(user(), "123456", 5);
        stale.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(emailVerificationCodePort.findByChallengeHash(anyString())).thenReturn(Optional.of(stale));

        Verification outcome = service.verify("raw-challenge", "123456");

        // A correct code is worthless once the challenge has expired.
        assertThat(outcome.status()).isEqualTo(Verification.Status.EXPIRED);
        verify(emailVerificationCodePort).delete(stale);
        verify(emailVerificationCodePort, never()).deleteByUserId(any());
    }

    @Test
    void verify_rejectsConsumedChallenge() {
        EmailVerificationCode consumed = challenge(user(), "123456", 0);
        consumed.setConsumedAt(LocalDateTime.now().minusSeconds(30));
        when(emailVerificationCodePort.findByChallengeHash(anyString())).thenReturn(Optional.of(consumed));

        Verification outcome = service.verify("raw-challenge", "123456");

        assertThat(outcome.status()).isEqualTo(Verification.Status.EXPIRED);
        verify(emailVerificationCodePort).delete(consumed);
    }

    // --- resend ---

    @Test
    void resend_emailsFreshCode_andKeepsAttempts() {
        User user = user();
        EmailVerificationCode vc = EmailVerificationCode.builder()
                .id(UUID.randomUUID()).user(user)
                .challengeHash("ch").codeHash(TokenHasher.hash("111111"))
                .attemptsRemaining(3)
                .expiresAt(LocalDateTime.now().plusMinutes(2))
                .build();
        when(emailVerificationCodePort.findByChallengeHash(anyString())).thenReturn(Optional.of(vc));

        ResendCodeResponse response = service.resend("raw-challenge");

        assertThat(response.getStatus()).isEqualTo("SENT");
        assertThat(response.getAttemptsRemaining()).isEqualTo(3);
        assertThat(response.getExpiresInSeconds()).isEqualTo(600L);
        assertThat(response.getMessage()).isEqualTo("A new code is on its way.");
        verify(emailVerificationCodePort).save(vc);

        ArgumentCaptor<String> emailedCode = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendLoginVerificationEmail(eq(user), emailedCode.capture(), eq(10L));
        // The old code is dead, the new one is what got emailed, and the clock restarted.
        assertThat(vc.getCodeHash()).isNotEqualTo(TokenHasher.hash("111111"));
        assertThat(vc.getCodeHash()).isEqualTo(TokenHasher.hash(emailedCode.getValue()));
        assertThat(vc.getExpiresAt()).isCloseTo(LocalDateTime.now().plusMinutes(10), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void resend_returnsExpired_forUnknownChallenge() {
        when(emailVerificationCodePort.findByChallengeHash(anyString())).thenReturn(Optional.empty());

        ResendCodeResponse response = service.resend("gone");

        assertThat(response.getStatus()).isEqualTo("EXPIRED");
        assertThat(response.getMessage()).isEqualTo("Your sign-in session expired. Please sign in again.");
        assertThat(response.getAttemptsRemaining()).isNull();
        verify(emailService, never()).sendLoginVerificationEmail(any(), anyString(), anyLong());
    }

    @Test
    void resend_returnsExpired_forConsumedChallenge() {
        EmailVerificationCode consumed = challenge(user(), "111111", 0);
        consumed.setConsumedAt(LocalDateTime.now());
        when(emailVerificationCodePort.findByChallengeHash(anyString())).thenReturn(Optional.of(consumed));

        ResendCodeResponse response = service.resend("raw-challenge");

        assertThat(response.getStatus()).isEqualTo("EXPIRED");
        verify(emailVerificationCodePort, never()).save(any());
        verify(emailService, never()).sendLoginVerificationEmail(any(), anyString(), anyLong());
    }
}
