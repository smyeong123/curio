package com.curio.user.port.out;

import com.curio.user.entity.UserApiKey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for the BYOK store.
 *
 * Implementations MUST return raw entities (with encrypted bytes) — encryption
 * lives in the service layer, not the repository.
 */
public interface UserApiKeyPort {

    Optional<UserApiKey> findByUserIdAndProvider(UUID userId, UserApiKey.Provider provider);

    List<UserApiKey> findByUserId(UUID userId);

    UserApiKey save(UserApiKey key);

    void delete(UserApiKey key);

    void deleteByUserIdAndProvider(UUID userId, UserApiKey.Provider provider);

    /** True if the user has at least one validated BYOK key. */
    boolean existsValidatedForUser(UUID userId);
}
