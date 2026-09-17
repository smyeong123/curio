package com.curio.quiz.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row of {@code GET /api/v1/quiz/history}: the user's best attempt on a quiz.
 *
 * @param completedAt when the stored (best) attempt was made; serialized as an ISO date-time
 */
public record QuizHistoryEntry(
        UUID id,
        UUID quizId,
        int score,
        int totalQuestions,
        LocalDateTime completedAt) {}
