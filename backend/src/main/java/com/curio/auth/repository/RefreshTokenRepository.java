package com.curio.auth.repository;

import com.curio.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void deleteByUserId(UUID userId);

    /**
     * Atomic conditional rotation: two requests presenting the same token race on
     * the row lock and only the first still sees {@code rotatedAt IS NULL}.
     * Deliberately no {@code clearAutomatically}: the caller still holds the
     * token's lazy {@code user} proxy and needs it to issue the successor.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.rotatedAt = :now WHERE r.id = :id AND r.rotatedAt IS NULL")
    int markRotated(@Param("id") UUID id, @Param("now") LocalDateTime now);
}
