package com.curio.news.adapter.persistence;

import com.curio.news.entity.Digest;
import com.curio.news.port.out.DigestPort;
import com.curio.news.repository.DigestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DigestJpaAdapter implements DigestPort {

    private final DigestRepository repository;

    /** Retention deletes run in bounded batches of this many digests per statement. */
    private static final int CLEANUP_BATCH_SIZE = 1000;

    @Override
    public Optional<Digest> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Page<Digest> findByUserIdOrderByGeneratedAtDesc(UUID userId, Pageable pageable) {
        return repository.findByUserIdOrderByGeneratedAtDesc(userId, pageable);
    }

    @Override
    public List<Digest> findTop5ByUserIdOrderByGeneratedAtDesc(UUID userId) {
        return repository.findTop5ByUserIdOrderByGeneratedAtDesc(userId);
    }

    @Override
    public Optional<Digest> findByEmailProviderId(String emailProviderId) {
        return repository.findByEmailProviderId(emailProviderId);
    }

    @Override
    public long countByUserId(UUID userId) {
        return repository.countByUserId(userId);
    }

    @Override
    public long countByEmailSentAtAfter(LocalDateTime date) {
        return repository.countByEmailSentAtAfter(date);
    }

    @Override
    public long countByGeneratedAtAfter(LocalDateTime date) {
        return repository.countByGeneratedAtAfter(date);
    }

    @Override
    public long deleteByGeneratedAtBefore(LocalDateTime date) {
        // Bounded batches instead of one giant DELETE: each statement's lock window
        // stays short (quiz/attempt rows cascade at the DB level per batch) and a
        // backlog can't blow past the cleanup job's ShedLock ceiling. Each
        // deleteAllByIdInBatch commits in its own transaction.
        long total = 0;
        while (true) {
            List<UUID> ids = repository.findIdsByGeneratedAtBefore(
                    date, PageRequest.of(0, CLEANUP_BATCH_SIZE));
            if (ids.isEmpty()) {
                return total;
            }
            repository.deleteAllByIdInBatch(ids);
            total += ids.size();
        }
    }

    @Override
    public Page<Digest> findAllByOrderByGeneratedAtDesc(Pageable pageable) {
        return repository.findAllByOrderByGeneratedAtDesc(pageable);
    }

    @Override
    public boolean existsByUserIdAndGeneratedAtBetween(UUID userId, LocalDateTime start, LocalDateTime end) {
        return repository.existsByUserIdAndGeneratedAtBetween(userId, start, end);
    }

    @Override
    public Page<Digest> findByUserIdAndGeneratedAtAfterOrderByGeneratedAtDesc(UUID userId, LocalDateTime after, Pageable pageable) {
        return repository.findByUserIdAndGeneratedAtAfterOrderByGeneratedAtDesc(userId, after, pageable);
    }

    @Override
    public Optional<Digest> findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(UUID userId) {
        return repository.findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(userId);
    }

    @Override
    public Page<Digest> findByTopicInContent(String topic, Pageable pageable) {
        String topicJson = "[\"" + topic.replace("\"", "\\\"") + "\"]";
        return repository.findByTopicInContent(topicJson, pageable);
    }

    @Override
    public Page<Digest> findByUserIdAndTopicInContent(UUID userId, String topic, Pageable pageable) {
        String topicJson = "[\"" + topic.replace("\"", "\\\"") + "\"]";
        return repository.findByUserIdAndTopicInContent(userId, topicJson, pageable);
    }

    @Override
    public Page<Digest> searchUserDigests(UUID userId, String query, Pageable pageable) {
        return repository.searchUserDigests(userId, query, pageable);
    }

    @Override
    public Digest save(Digest digest) {
        return repository.save(digest);
    }

    @Override
    public int claimForEmailSend(UUID id, LocalDateTime sentAt) {
        return repository.claimForEmailSend(id, sentAt);
    }

    @Override
    public void releaseEmailClaim(UUID id) {
        repository.releaseEmailClaim(id);
    }

    @Override
    public int updateEmailProviderId(UUID id, String providerMessageId) {
        return repository.updateEmailProviderId(id, providerMessageId);
    }
}
