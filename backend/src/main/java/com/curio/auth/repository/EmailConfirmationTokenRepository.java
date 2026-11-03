package com.curio.auth.repository;

import com.curio.auth.entity.EmailConfirmationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailConfirmationTokenRepository extends JpaRepository<EmailConfirmationToken, UUID> {
    Optional<EmailConfirmationToken> findByTokenHash(String tokenHash);

    void deleteByUserId(UUID userId);

    /** Atomic single-use claim: 1 if this call consumed the token, 0 if it was already used. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE EmailConfirmationToken t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
    int markUsed(@Param("id") UUID id, @Param("now") LocalDateTime now);
}
