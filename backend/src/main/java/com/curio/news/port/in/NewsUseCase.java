package com.curio.news.port.in;

import com.curio.news.dto.DigestResponse;
import com.curio.user.entity.User;
import org.springframework.data.domain.Page;

import java.util.UUID;

/**
 * Inbound port for news digest use cases.
 * Controllers and the digest pipeline (driving adapters) depend on this
 * interface, not on the concrete NewsService implementation.
 */
public interface NewsUseCase {

    Page<DigestResponse> getDigests(UUID userId, int page, int size);

    /** Postgres full-text search over the user's own digest archive. */
    Page<DigestResponse> searchDigests(UUID userId, String query, int page, int size);

    DigestResponse getDigest(UUID digestId, UUID userId);

    /**
     * Generate today's digest for the user, reporting per-topic progress to the
     * listener (null for none), and say exactly what happened (see
     * {@link DigestGeneration.Status}).
     */
    DigestGeneration generate(User user, DigestProgressListener progressListener);
}
