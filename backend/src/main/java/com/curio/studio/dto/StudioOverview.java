package com.curio.studio.dto;

import com.curio.news.entity.Digest;

import java.util.List;

/**
 * The context the Studio page shows around the two task cards.
 *
 * @param topics          the beats the digest is built from (empty until preferences are saved)
 * @param latestDigest    the most recently generated digest, or null when there is none yet
 * @param hasUnsentDigest whether a digest is waiting to be emailed
 */
public record StudioOverview(
        String email,
        List<String> topics,
        int topicCount,
        LatestDigest latestDigest,
        boolean hasUnsentDigest) {

    /** Timestamps are ISO local date-time text (no zone), null when unset. */
    public record LatestDigest(String id, String generatedAt, String emailSentAt) {

        public static LatestDigest from(Digest digest) {
            return new LatestDigest(
                    digest.getId().toString(),
                    digest.getGeneratedAt() != null ? digest.getGeneratedAt().toString() : null,
                    digest.getEmailSentAt() != null ? digest.getEmailSentAt().toString() : null);
        }
    }
}
