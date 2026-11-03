package com.curio.auth.port.out;

import com.curio.auth.entity.EmailConfirmationToken;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for sign-up email-confirmation-token persistence.
 * Domain services depend on this interface; the JPA adapter is the
 * infrastructure implementation.
 */
public interface EmailConfirmationTokenPort {

    Optional<EmailConfirmationToken> findByTokenHash(String tokenHash);

    EmailConfirmationToken save(EmailConfirmationToken confirmationToken);

    void deleteByUserId(UUID userId);

    /**
     * Atomic conditional UPDATE ({@code WHERE used_at IS NULL}): returns 1 if this
     * call consumed the token, 0 if a concurrent confirm already did.
     */
    int markUsed(UUID id, LocalDateTime usedAt);
}
