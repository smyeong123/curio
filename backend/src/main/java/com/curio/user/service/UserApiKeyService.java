package com.curio.user.service;

import com.curio.admin.port.in.AuditLogUseCase;
import com.curio.shared.exception.ResourceNotFoundException;
import com.curio.shared.exception.UnauthorizedException;
import com.curio.shared.security.ApiKeyCipher;
import com.curio.user.dto.ApiKeySummary;
import com.curio.user.entity.User;
import com.curio.user.entity.UserApiKey;
import com.curio.user.port.in.ApiKeyValidator;
import com.curio.user.port.in.UserApiKeyUseCase;
import com.curio.user.port.out.UserApiKeyPort;
import com.curio.user.port.out.UserPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserApiKeyService implements UserApiKeyUseCase {

    private final UserApiKeyPort userApiKeyPort;
    private final UserPort userPort;
    private final PasswordEncoder passwordEncoder;
    private final ApiKeyCipher cipher;
    // ObjectProvider so the validator (which lives in news/) can be optional
    // during early bring-up and tests can pass a mock without wiring all 3 LLMs.
    private final ObjectProvider<ApiKeyValidator> validatorProvider;
    // Security-sensitive key lifecycle (add/rotate/delete) is audited. The audit
    // writer reads the actor from the SecurityContext and never throws.
    private final AuditLogUseCase auditLog;

    @Override
    @Transactional(readOnly = true)
    public List<ApiKeySummary> listForUser(UUID userId) {
        return userApiKeyPort.findByUserId(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional
    public ApiKeySummary saveKey(UUID userId, UserApiKey.Provider provider, String rawApiKey,
                                 String currentPassword) {
        User user = requirePasswordReauth(userId, currentPassword);

        String trimmed = rawApiKey == null ? "" : rawApiKey.strip();
        if (trimmed.length() < 16) {
            throw new IllegalArgumentException("API key looks too short to be valid");
        }

        // Always validate before persisting. A key with validatedAt == null is inert:
        // the digest/quiz pipeline never resolves it, so a "skip validation" path would
        // silently store a key that does nothing while billing falls back to the
        // platform key. (The old opt-out `validate` request flag was removed.)
        boolean validatedNow = false;
        ApiKeyValidator v = validatorProvider.getIfAvailable();
        if (v == null) {
            log.warn("ApiKeyValidator bean not available — saving key WITHOUT live validation");
        } else {
            validatedNow = v.isValid(provider, trimmed);
            if (!validatedNow) {
                throw new IllegalArgumentException(
                        "Provider rejected this key. Double-check it works in their dashboard.");
            }
        }

        ApiKeyCipher.Sealed sealed = cipher.encrypt(trimmed);
        String preview = preview(trimmed);

        UserApiKey existing = userApiKeyPort.findByUserIdAndProvider(userId, provider).orElse(null);
        boolean rotated = existing != null;
        UserApiKey toSave;
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setEncryptedKey(sealed.ciphertext());
            existing.setKeyIv(sealed.iv());
            existing.setKeyPreview(preview);
            existing.setValidatedAt(validatedNow ? now : null);
            existing.setLastUsedAt(null);
            existing.setRotatedAt(now);
            toSave = existing;
            log.info("BYOK key rotated for user {} provider {}", userId, provider);
        } else {
            toSave = UserApiKey.builder()
                    .user(user)
                    .provider(provider)
                    .encryptedKey(sealed.ciphertext())
                    .keyIv(sealed.iv())
                    .keyPreview(preview)
                    .validatedAt(validatedNow ? now : null)
                    .rotatedAt(now)
                    .build();
            log.info("BYOK key added for user {} provider {}", userId, provider);
        }
        UserApiKey saved = userApiKeyPort.save(toSave);
        auditLog.record(
                rotated ? "BYOK_KEY_ROTATED" : "BYOK_KEY_ADDED",
                "USER_API_KEY",
                provider.name(),
                Map.of("provider", provider.name(), "validated", validatedNow));
        return toSummary(saved);
    }

    @Override
    @Transactional
    public void deleteKey(UUID userId, UserApiKey.Provider provider, String currentPassword) {
        requirePasswordReauth(userId, currentPassword);
        userApiKeyPort.deleteByUserIdAndProvider(userId, provider);
        auditLog.record("BYOK_KEY_DELETED", "USER_API_KEY", provider.name(),
                Map.of("provider", provider.name()));
        log.info("BYOK key deleted for user {} provider {}", userId, provider);
    }

    @Override
    public boolean validateKey(UserApiKey.Provider provider, String rawApiKey) {
        ApiKeyValidator v = validatorProvider.getIfAvailable();
        if (v == null) {
            log.warn("ApiKeyValidator bean not available — validation skipped");
            return false;
        }
        return v.isValid(provider, rawApiKey == null ? "" : rawApiKey.strip());
    }

    @Override
    @Transactional
    public Optional<String> getDecryptedKey(UUID userId, UserApiKey.Provider provider) {
        return userApiKeyPort.findByUserIdAndProvider(userId, provider)
                .filter(k -> k.getValidatedAt() != null)
                .map(k -> {
                    k.setLastUsedAt(LocalDateTime.now());
                    userApiKeyPort.save(k);
                    return cipher.decrypt(k.getEncryptedKey(), k.getKeyIv());
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserApiKey.Provider> resolveProviderForUser(UUID userId, UserApiKey.Provider preferred) {
        // BYOK only engages when the user has a validated key for the platform's
        // currently configured provider (no per-user provider switching). Handing a
        // different provider's key to the active LLM client would 401 every call, so
        // there is deliberately no cross-provider fallback — callers fall back to the
        // platform key when this returns empty.
        if (preferred != null && userApiKeyPort.findByUserIdAndProvider(userId, preferred)
                .filter(k -> k.getValidatedAt() != null).isPresent()) {
            return Optional.of(preferred);
        }
        return Optional.empty();
    }

    // ── helpers ───────────────────────────────────────────────────

    private User requirePasswordReauth(UUID userId, String currentPassword) {
        User user = userPort.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getPasswordHash() == null) {
            // OAuth-only users — re-auth-by-password is not possible; reject for now.
            throw new UnauthorizedException(
                    "Set a password in settings before managing API keys");
        }
        if (currentPassword == null
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        return user;
    }

    /** "sk-ant-...XYZW" — first 7 + last 4. Defends against accidental full-key UI display. */
    private String preview(String raw) {
        int n = raw.length();
        if (n <= 11) {
            // Too short to mask sensibly — show only the last 4.
            return "...." + raw.substring(Math.max(0, n - 4));
        }
        return raw.substring(0, 7) + "..." + raw.substring(n - 4);
    }

    private ApiKeySummary toSummary(UserApiKey k) {
        return ApiKeySummary.builder()
                .provider(k.getProvider())
                .keyPreview(k.getKeyPreview())
                .validated(k.getValidatedAt() != null)
                .validatedAt(k.getValidatedAt())
                .lastUsedAt(k.getLastUsedAt())
                .updatedAt(k.getUpdatedAt())
                .rotatedAt(k.getRotatedAt())
                .stale(k.isStale())
                .build();
    }
}
