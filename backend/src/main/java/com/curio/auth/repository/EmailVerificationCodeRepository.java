package com.curio.auth.repository;

import com.curio.auth.entity.EmailVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, UUID> {
    Optional<EmailVerificationCode> findByChallengeHash(String challengeHash);
    void deleteByUserId(UUID userId);

    /**
     * Atomic conditional decrement: parallel wrong guesses each consume exactly one
     * attempt at the database level, so the budget can't be exceeded by racing the
     * read-modify-write cycle. Returns 0 rows when the budget is already exhausted
     * or the challenge is consumed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EmailVerificationCode e SET e.attemptsRemaining = e.attemptsRemaining - 1 "
            + "WHERE e.id = :id AND e.attemptsRemaining > 0 AND e.consumedAt IS NULL")
    int decrementAttempts(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EmailVerificationCode e SET e.consumedAt = :now "
            + "WHERE e.id = :id AND e.consumedAt IS NULL")
    int markConsumed(@Param("id") UUID id, @Param("now") LocalDateTime now);
}
