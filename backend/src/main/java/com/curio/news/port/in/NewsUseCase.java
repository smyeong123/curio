package com.curio.news.port.in;

import com.curio.news.dto.DigestResponse;
import com.curio.news.entity.Digest;
import com.curio.user.entity.User;
import org.springframework.data.domain.Page;

import java.util.UUID;

/**
 * Inbound port for news digest use cases.
 * Controllers and scheduled jobs (driving adapters) depend on this interface,
 * not on the concrete NewsService implementation.
 */
public interface NewsUseCase {

    Page<DigestResponse> getDigests(UUID userId, int page, int size);

    /** Postgres full-text search over the user's own digest archive. */
    Page<DigestResponse> searchDigests(UUID userId, String query, int page, int size);

    DigestResponse getDigest(UUID digestId, UUID userId);

    Digest generateDigestForUser(User user);

    /**
     * Generate a digest for the user, reporting per-topic progress to the given
     * listener. Used by the user-facing Digest Studio to drive a live progress UI.
     */
    Digest generateDigestForUser(User user, DigestProgressListener progressListener);
}
