package com.curio.user.service;

import com.curio.shared.exception.ResourceNotFoundException;
import com.curio.shared.exception.UnauthorizedException;
import com.curio.shared.security.ApiKeyCipher;
import com.curio.user.dto.ApiKeySummary;
import com.curio.user.entity.User;
import com.curio.user.entity.UserApiKey;
import com.curio.user.port.in.ApiKeyValidator;
import com.curio.user.port.out.UserApiKeyPort;
import com.curio.user.port.out.UserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserApiKeyServiceTest {

    @Mock private UserApiKeyPort userApiKeyPort;
    @Mock private UserPort userPort;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApiKeyValidator validator;
    @Mock private ObjectProvider<ApiKeyValidator> validatorProvider;
    @Mock private com.curio.admin.port.in.AuditLogUseCase auditLog;

    private ApiKeyCipher cipher;
    private UserApiKeyService service;
    private UUID userId;
    private User testUser;

    @BeforeEach
    void setUp() {
        // A real (non-zero) 32-byte key — the all-zero placeholder is now rejected
        // outside dev/test. This test only needs a working cipher.
        cipher = new ApiKeyCipher(
                java.util.Base64.getEncoder().encodeToString("curio-test-key-0123456789abcdef!".getBytes()),
                new MockEnvironment());
        cipher.init();
        service = new UserApiKeyService(userApiKeyPort, userPort, passwordEncoder, cipher, validatorProvider, auditLog);
        userId = UUID.randomUUID();
        testUser = User.builder().id(userId).email("u@e.com").passwordHash("$hash").build();
    }

    // ── saveKey ───────────────────────────────────────────────

    @Test
    void saveKey_persistsEncryptedAndReturnsSummaryWithoutRawKey() {
        when(userPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("pw", "$hash")).thenReturn(true);
        when(validatorProvider.getIfAvailable()).thenReturn(validator);
        when(validator.isValid(UserApiKey.Provider.CLAUDE, "sk-ant-api03-1234567890abcdef")).thenReturn(true);
        when(userApiKeyPort.findByUserIdAndProvider(userId, UserApiKey.Provider.CLAUDE))
                .thenReturn(Optional.empty());
        when(userApiKeyPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApiKeySummary out = service.saveKey(userId, UserApiKey.Provider.CLAUDE,
                "sk-ant-api03-1234567890abcdef", "pw");

        ArgumentCaptor<UserApiKey> captor = ArgumentCaptor.forClass(UserApiKey.class);
        verify(userApiKeyPort).save(captor.capture());
        UserApiKey saved = captor.getValue();

        // Stored key is NOT plaintext.
        assertThat(new String(saved.getEncryptedKey())).doesNotContain("sk-ant-api03");
        // IV present and 12 bytes (GCM).
        assertThat(saved.getKeyIv()).hasSize(12);
        // Decrypts back to the original.
        assertThat(cipher.decrypt(saved.getEncryptedKey(), saved.getKeyIv()))
                .isEqualTo("sk-ant-api03-1234567890abcdef");
        // Validated timestamp set.
        assertThat(saved.getValidatedAt()).isNotNull();
        // Preview is masked.
        assertThat(saved.getKeyPreview()).startsWith("sk-ant-").endsWith("cdef");
        assertThat(saved.getKeyPreview()).doesNotContain("api03-12345");
        // Returned summary contains no raw key.
        assertThat(out.isValidated()).isTrue();
        assertThat(out.getKeyPreview()).isEqualTo(saved.getKeyPreview());
    }

    @Test
    void saveKey_rejects_wrongPassword() {
        when(userPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong", "$hash")).thenReturn(false);

        assertThatThrownBy(() ->
                service.saveKey(userId, UserApiKey.Provider.CLAUDE, "sk-validkeyabcdefgh", "wrong"))
                .isInstanceOf(UnauthorizedException.class);

        verify(userApiKeyPort, never()).save(any());
    }

    @Test
    void saveKey_rejects_oauthOnlyUserWithoutPassword() {
        User oauthUser = User.builder().id(userId).email("g@e.com").passwordHash(null).googleId("g123").build();
        when(userPort.findById(userId)).thenReturn(Optional.of(oauthUser));

        assertThatThrownBy(() ->
                service.saveKey(userId, UserApiKey.Provider.CLAUDE, "sk-validkeyabcdefgh", "pw"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Set a password");
    }

    @Test
    void saveKey_rejects_unknownUser() {
        when(userPort.findById(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() ->
                service.saveKey(userId, UserApiKey.Provider.CLAUDE, "sk-validkeyabcdefgh", "pw"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void saveKey_rejects_keyTooShort() {
        when(userPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("pw", "$hash")).thenReturn(true);

        assertThatThrownBy(() ->
                service.saveKey(userId, UserApiKey.Provider.CLAUDE, "tiny", "pw"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void saveKey_rejects_invalidKey_perValidator() {
        when(userPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("pw", "$hash")).thenReturn(true);
        when(validatorProvider.getIfAvailable()).thenReturn(validator);
        when(validator.isValid(any(), any())).thenReturn(false);

        assertThatThrownBy(() ->
                service.saveKey(userId, UserApiKey.Provider.CLAUDE, "sk-rejected-by-provider-12345", "pw"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejected this key");

        verify(userApiKeyPort, never()).save(any());
    }

    @Test
    void saveKey_alwaysValidates_ignoringValidateFalse() {
        when(userPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("pw", "$hash")).thenReturn(true);
        when(validatorProvider.getIfAvailable()).thenReturn(validator);
        when(validator.isValid(UserApiKey.Provider.OPENAI, "sk-not-validated-zzzzz")).thenReturn(true);
        when(userApiKeyPort.findByUserIdAndProvider(userId, UserApiKey.Provider.OPENAI))
                .thenReturn(Optional.empty());
        when(userApiKeyPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // A key is always validated before save so it can never be stored inert
        // (permanently unusable) — the old opt-out flag no longer exists.
        ApiKeySummary out = service.saveKey(userId, UserApiKey.Provider.OPENAI,
                "sk-not-validated-zzzzz", "pw");

        assertThat(out.isValidated()).isTrue();
        verify(validatorProvider).getIfAvailable();
    }

    // ── deleteKey ─────────────────────────────────────────────

    @Test
    void deleteKey_passesPasswordCheck_thenDeletes() {
        when(userPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("pw", "$hash")).thenReturn(true);

        service.deleteKey(userId, UserApiKey.Provider.GEMINI, "pw");

        verify(userApiKeyPort).deleteByUserIdAndProvider(userId, UserApiKey.Provider.GEMINI);
    }

    @Test
    void deleteKey_rejects_wrongPassword() {
        when(userPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.deleteKey(userId, UserApiKey.Provider.GEMINI, "wrong"))
                .isInstanceOf(UnauthorizedException.class);
        verify(userApiKeyPort, never()).deleteByUserIdAndProvider(any(), any());
    }

    // ── getDecryptedKey ──────────────────────────────────────

    @Test
    void getDecryptedKey_returnsPlain_whenValidated_andStampsLastUsed() {
        ApiKeyCipher.Sealed sealed = cipher.encrypt("sk-stored-via-cipher");
        UserApiKey row = UserApiKey.builder()
                .id(UUID.randomUUID()).user(testUser).provider(UserApiKey.Provider.CLAUDE)
                .encryptedKey(sealed.ciphertext()).keyIv(sealed.iv())
                .validatedAt(java.time.LocalDateTime.now())
                .build();
        when(userApiKeyPort.findByUserIdAndProvider(userId, UserApiKey.Provider.CLAUDE))
                .thenReturn(Optional.of(row));

        Optional<String> out = service.getDecryptedKey(userId, UserApiKey.Provider.CLAUDE);

        assertThat(out).contains("sk-stored-via-cipher");
        assertThat(row.getLastUsedAt()).isNotNull();
        verify(userApiKeyPort).save(row);
    }

    @Test
    void getDecryptedKey_returnsEmpty_whenNotValidated() {
        UserApiKey row = UserApiKey.builder()
                .id(UUID.randomUUID()).user(testUser).provider(UserApiKey.Provider.CLAUDE)
                .encryptedKey(new byte[]{1,2,3}).keyIv(new byte[12])
                .validatedAt(null)   // never validated
                .build();
        when(userApiKeyPort.findByUserIdAndProvider(userId, UserApiKey.Provider.CLAUDE))
                .thenReturn(Optional.of(row));

        assertThat(service.getDecryptedKey(userId, UserApiKey.Provider.CLAUDE)).isEmpty();
    }

    @Test
    void getDecryptedKey_returnsEmpty_whenAbsent() {
        when(userApiKeyPort.findByUserIdAndProvider(any(), any())).thenReturn(Optional.empty());
        assertThat(service.getDecryptedKey(userId, UserApiKey.Provider.CLAUDE)).isEmpty();
    }

    // ── listForUser ──────────────────────────────────────────

    @Test
    void listForUser_returnsSummariesWithoutEncryptedFields() {
        UserApiKey row = UserApiKey.builder()
                .id(UUID.randomUUID()).user(testUser).provider(UserApiKey.Provider.OPENAI)
                .encryptedKey(new byte[]{42}).keyIv(new byte[12])
                .keyPreview("sk-xxxx...abcd")
                .validatedAt(java.time.LocalDateTime.now())
                .build();
        when(userApiKeyPort.findByUserId(userId)).thenReturn(List.of(row));

        List<ApiKeySummary> out = service.listForUser(userId);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getKeyPreview()).isEqualTo("sk-xxxx...abcd");
        assertThat(out.get(0).isValidated()).isTrue();
    }

    // ── resolveProviderForUser ───────────────────────────────

    @Test
    void resolveProvider_prefersConfiguredProvider() {
        UserApiKey claudeKey = UserApiKey.builder().id(UUID.randomUUID()).user(testUser)
                .provider(UserApiKey.Provider.CLAUDE).validatedAt(java.time.LocalDateTime.now())
                .encryptedKey(new byte[]{0}).keyIv(new byte[12]).build();
        when(userApiKeyPort.findByUserIdAndProvider(userId, UserApiKey.Provider.CLAUDE))
                .thenReturn(Optional.of(claudeKey));

        Optional<UserApiKey.Provider> p = service.resolveProviderForUser(userId, UserApiKey.Provider.CLAUDE);
        assertThat(p).contains(UserApiKey.Provider.CLAUDE);
    }

    @Test
    void resolveProvider_noCrossProviderFallback() {
        // No validated key for the configured provider (CLAUDE) → BYOK does not
        // engage, even if the user has a validated key for a DIFFERENT provider
        // (that key would 401 against the active client). Caller uses the platform key.
        when(userApiKeyPort.findByUserIdAndProvider(userId, UserApiKey.Provider.CLAUDE))
                .thenReturn(Optional.empty());

        Optional<UserApiKey.Provider> p = service.resolveProviderForUser(userId, UserApiKey.Provider.CLAUDE);
        assertThat(p).isEmpty();
    }
}
