package com.curio.news.port.out;

import com.curio.news.entity.Digest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for digest persistence.
 * Domain services depend on this interface; the JPA adapter (DigestRepository)
 * is the infrastructure implementation.
 */
public interface DigestPort {

    Optional<Digest> findById(UUID id);

    Page<Digest> findByUserIdOrderByGeneratedAtDesc(UUID userId, Pageable pageable);

    List<Digest> findTop5ByUserIdOrderByGeneratedAtDesc(UUID userId);

    Optional<Digest> findByEmailProviderId(String emailProviderId);

    long countByUserId(UUID userId);

    long countByEmailSentAtAfter(LocalDateTime date);

    long countByGeneratedAtAfter(LocalDateTime date);

    /**
     * Delete all digests generated before the cutoff (quizzes/attempts cascade at
     * the DB level), in bounded batches so no single statement holds a long lock.
     * @return the number of digests deleted
     */
    long deleteByGeneratedAtBefore(LocalDateTime date);

    Page<Digest> findAllByOrderByGeneratedAtDesc(Pageable pageable);

    boolean existsByUserIdAndGeneratedAtBetween(UUID userId, LocalDateTime start, LocalDateTime end);

    Page<Digest> findByUserIdAndGeneratedAtAfterOrderByGeneratedAtDesc(UUID userId, LocalDateTime after, Pageable pageable);

    Optional<Digest> findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(UUID userId);

    Page<Digest> findByTopicInContent(String topic, Pageable pageable);

    Page<Digest> findByUserIdAndTopicInContent(UUID userId, String topic, Pageable pageable);

    /** Full-text search across a user's digests (Postgres tsvector). */
    Page<Digest> searchUserDigests(UUID userId, String query, Pageable pageable);

    Digest save(Digest digest);

    /**
     * Atomically claims the right to email this digest (sets email_sent_at only if
     * still NULL). Returns 1 if this caller won the claim, 0 if another send path
     * already sent or is sending it.
     */
    int claimForEmailSend(UUID id, LocalDateTime sentAt);

    /** Frees a claimed digest after a failed provider send so a later run retries it. */
    void releaseEmailClaim(UUID id);

    /** Records the provider message id without touching other columns. */
    int updateEmailProviderId(UUID id, String providerMessageId);
}
