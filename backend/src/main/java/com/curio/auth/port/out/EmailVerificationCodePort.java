package com.curio.auth.port.out;

import com.curio.auth.entity.EmailVerificationCode;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for login email-verification-code persistence.
 * Domain services depend on this interface; the JPA adapter
 * (EmailVerificationCodeRepository) is the infrastructure implementation.
 */
public interface EmailVerificationCodePort {

    Optional<EmailVerificationCode> findByChallengeHash(String challengeHash);

    EmailVerificationCode save(EmailVerificationCode code);

    void delete(EmailVerificationCode code);

    void deleteByUserId(UUID userId);

    /**
     * Atomically consumes one attempt and returns how many remain. Safe under
     * concurrent calls: each successful call burns exactly one attempt at the
     * database level. Returns 0 when the challenge is already exhausted or consumed.
     */
    int decrementAttempts(UUID id);

    /** Marks the challenge consumed (idempotent). */
    void markConsumed(UUID id);
}
