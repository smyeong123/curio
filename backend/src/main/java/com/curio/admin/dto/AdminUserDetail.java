package com.curio.admin.dto;

import com.curio.news.entity.Digest;
import com.curio.quiz.entity.QuizAttempt;
import com.curio.user.dto.UserResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code GET /api/v1/admin/users/{id}}: the reader's profile, the beats they
 * follow, engagement metrics and their five most recent digests and quiz attempts.
 */
public record AdminUserDetail(
        UserResponse user,
        List<String> topics,
        Metrics metrics,
        List<RecentDigest> recentDigests,
        List<RecentQuizAttempt> recentQuizAttempts) {

    /**
     * @param averageQuizScore mean score across all attempts, rounded to two decimals;
     *                         null until the reader has taken a quiz
     */
    public record Metrics(long digestCount, long quizAttempts, Double averageQuizScore) {

        public static Metrics of(long digestCount, long quizAttempts, Double rawAverage) {
            Double rounded = rawAverage != null ? Math.round(rawAverage * 100.0) / 100.0 : null;
            return new Metrics(digestCount, quizAttempts, rounded);
        }
    }

    public record RecentDigest(UUID id, LocalDateTime generatedAt) {

        public static RecentDigest from(Digest digest) {
            return new RecentDigest(digest.getId(), digest.getGeneratedAt());
        }
    }

    public record RecentQuizAttempt(UUID id, LocalDateTime completedAt, Integer score) {

        public static RecentQuizAttempt from(QuizAttempt attempt) {
            return new RecentQuizAttempt(attempt.getId(), attempt.getCompletedAt(), attempt.getScore());
        }
    }
}
