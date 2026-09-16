package com.curio.shared.scheduler;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reaps expired authentication rows so those tables (and their {@code expires_at}
 * indexes) do not grow without bound. Driven daily by {@link CleanupJob}.
 *
 * <p>Kept as a dedicated bean so its bulk delete runs in its own transaction,
 * isolated from the digest cleanup: a failure in one does not abort the other.
 *
 * <p>Bulk JPQL DELETEs issue a single SQL statement (no entity load). The
 * {@code password_reset_tokens} and {@code email_verification_codes} tables have
 * an {@code expires_at} index that the WHERE predicate uses; {@code refresh_tokens}
 * has no such index yet, so its reap is a full scan — acceptable for a once-daily
 * job. Consumed-but-not-yet-expired rows (used reset tokens, spent codes) are left
 * until they pass {@code expires_at}, which bounds them to their short TTL anyway.
 */
@Component
@Slf4j
public class ExpiredAuthRowReaper {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Deletes every refresh token, password-reset token and email-verification
     * code whose {@code expires_at} is before {@code cutoff}.
     *
     * @return an ordered map of table label to number of rows deleted
     */
    @Transactional
    public Map<String, Integer> reapExpired(LocalDateTime cutoff) {
        Map<String, Integer> reaped = new LinkedHashMap<>();
        reaped.put("expiredRefreshTokens", deleteExpired("RefreshToken", cutoff));
        reaped.put("expiredPasswordResetTokens", deleteExpired("PasswordResetToken", cutoff));
        reaped.put("expiredEmailVerificationCodes", deleteExpired("EmailVerificationCode", cutoff));
        return reaped;
    }

    // entityName is always a compile-time literal below — never user input.
    private int deleteExpired(String entityName, LocalDateTime cutoff) {
        return entityManager
                .createQuery("delete from " + entityName + " e where e.expiresAt < :cutoff")
                .setParameter("cutoff", cutoff)
                .executeUpdate();
    }
}
