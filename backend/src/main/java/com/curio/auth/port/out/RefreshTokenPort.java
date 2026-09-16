package com.curio.auth.port.out;

import com.curio.auth.entity.RefreshToken;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for refresh-token persistence.
 * Domain services depend on this interface; the JPA adapter
 * (RefreshTokenRepository) is the infrastructure implementation.
 */
public interface RefreshTokenPort {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    RefreshToken save(RefreshToken refreshToken);

    void delete(RefreshToken refreshToken);

    void deleteByUserId(UUID userId);
}
