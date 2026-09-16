package com.curio.user.port.in;

import com.curio.user.dto.ApiKeySummary;
import com.curio.user.entity.UserApiKey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserApiKeyUseCase {

    /** All keys this user has on file (metadata only — never the raw key). */
    List<ApiKeySummary> listForUser(UUID userId);

    /**
     * Add or replace the user's key for {@code provider}.
     * Verifies the user's current password and (optionally) the key with the LLM provider
     * before persisting.
     */
    ApiKeySummary saveKey(UUID userId, UserApiKey.Provider provider, String rawApiKey,
                          String currentPassword);

    /** Verify the user's current password, then delete the key. */
    void deleteKey(UUID userId, UserApiKey.Provider provider, String currentPassword);

    /** Live validation only — does not persist. Returns true on 2xx. */
    boolean validateKey(UserApiKey.Provider provider, String rawApiKey);

    /**
     * INTERNAL — used only by the digest pipeline to fetch the user's plaintext key
     * for a given provider. Updates {@code last_used_at} on hit.
     * Empty if user has no validated key for that provider.
     */
    Optional<String> getDecryptedKey(UUID userId, UserApiKey.Provider provider);

    /** Convenience for the digest pipeline: which provider to use for this user. */
    Optional<UserApiKey.Provider> resolveProviderForUser(UUID userId, UserApiKey.Provider preferred);

    /**
     * Convenience for the AI pipeline: the user's BYOK plaintext key for the
     * platform's {@code preferred} provider, or empty if they have none. Combines
     * {@link #resolveProviderForUser} + {@link #getDecryptedKey} so digest and quiz
     * generation share one resolution path. Empty → caller falls back to the
     * platform key.
     */
    default Optional<String> resolveDecryptedKey(UUID userId, UserApiKey.Provider preferred) {
        return resolveProviderForUser(userId, preferred)
                .flatMap(p -> getDecryptedKey(userId, p));
    }
}
