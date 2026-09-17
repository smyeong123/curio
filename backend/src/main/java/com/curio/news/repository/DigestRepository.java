package com.curio.news.repository;

import com.curio.news.entity.Digest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DigestRepository extends JpaRepository<Digest, UUID> {
    Page<Digest> findByUserIdOrderByGeneratedAtDesc(UUID userId, Pageable pageable);
    List<Digest> findTop5ByUserIdOrderByGeneratedAtDesc(UUID userId);
    Optional<Digest> findByEmailProviderId(String emailProviderId);
    long countByUserId(UUID userId);
    long countByEmailSentAtAfter(LocalDateTime date);
    /** Ids of digests past the retention cutoff, bounded by the Pageable — used for batched cleanup deletes. */
    @Query("SELECT d.id FROM Digest d WHERE d.generatedAt < :cutoff")
    List<UUID> findIdsByGeneratedAtBefore(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);
    Page<Digest> findAllByOrderByGeneratedAtDesc(Pageable pageable);
    boolean existsByUserIdAndGeneratedAtBetween(UUID userId, LocalDateTime start, LocalDateTime end);

    Optional<Digest> findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(UUID userId);

    Optional<Digest> findFirstByUserIdOrderByGeneratedAtDesc(UUID userId);

    // Atomic claim of the right to email a digest. Competing send paths (hourly
    // EmailSendJob, admin batch, Studio) all race through here; the conditional
    // update guarantees exactly one winner, so a user can never get the same
    // digest twice. @Transactional commits the claim before the email goes out
    // (send paths hold no outer transaction).
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Digest d SET d.emailSentAt = :now WHERE d.id = :id AND d.emailSentAt IS NULL")
    int claimForEmailSend(@Param("id") UUID id, @Param("now") LocalDateTime now);

    // Compensation when the provider send fails after a successful claim: free the
    // digest so a later run can retry it.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Digest d SET d.emailSentAt = NULL, d.emailProviderId = NULL WHERE d.id = :id")
    void releaseEmailClaim(@Param("id") UUID id);

    // Targeted update — the sender's in-memory entity is stale after the claim's
    // bulk update, so a full save() would overwrite email_sent_at back to NULL.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Digest d SET d.emailProviderId = :pid WHERE d.id = :id")
    int updateEmailProviderId(@Param("id") UUID id, @Param("pid") String providerMessageId);

    @Query(value = "SELECT * FROM digests WHERE content->'generatedFor' @> CAST(:topicJson AS jsonb) ORDER BY generated_at DESC",
           countQuery = "SELECT count(*) FROM digests WHERE content->'generatedFor' @> CAST(:topicJson AS jsonb)",
           nativeQuery = true)
    Page<Digest> findByTopicInContent(@Param("topicJson") String topicJson, Pageable pageable);

    @Query(value = "SELECT * FROM digests WHERE user_id = :userId AND content->'generatedFor' @> CAST(:topicJson AS jsonb) ORDER BY generated_at DESC",
           countQuery = "SELECT count(*) FROM digests WHERE user_id = :userId AND content->'generatedFor' @> CAST(:topicJson AS jsonb)",
           nativeQuery = true)
    Page<Digest> findByUserIdAndTopicInContent(@Param("userId") UUID userId, @Param("topicJson") String topicJson, Pageable pageable);

    // Full-text search via the GIN index in V21. plainto_tsquery escapes user
    // input safely so we don't have to sanitize for tsquery operators.
    @Query(value = "SELECT * FROM digests WHERE user_id = :userId " +
                   "AND to_tsvector('english', CAST(content AS text)) @@ plainto_tsquery('english', :q) " +
                   "ORDER BY generated_at DESC",
           countQuery = "SELECT count(*) FROM digests WHERE user_id = :userId " +
                        "AND to_tsvector('english', CAST(content AS text)) @@ plainto_tsquery('english', :q)",
           nativeQuery = true)
    Page<Digest> searchUserDigests(@Param("userId") UUID userId, @Param("q") String query, Pageable pageable);
}
