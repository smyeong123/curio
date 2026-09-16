package com.curio.auth.port.out;

import com.curio.auth.entity.PasswordResetToken;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for password-reset-token persistence.
 * Domain services depend on this interface; the JPA adapter
 * (PasswordResetTokenRepository) is the infrastructure implementation.
 */
public interface PasswordResetTokenPort {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    PasswordResetToken save(PasswordResetToken resetToken);

    void deleteByUserId(UUID userId);
}
