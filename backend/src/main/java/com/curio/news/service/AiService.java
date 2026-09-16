package com.curio.news.service;

import com.curio.news.dto.NewsSummary;
import com.curio.news.dto.QuizGenerationResult;

import java.util.List;

public interface AiService {

    /** Use the platform's configured key. */
    List<NewsSummary> generateNewsSummaries(String topic);

    /** Use the platform's configured key. */
    QuizGenerationResult generateQuizQuestions(String digestContent);

    /**
     * BYOK overload — substitute a user-supplied key for this single call.
     * If {@code overrideApiKey} is null/blank, behaves exactly like
     * {@link #generateNewsSummaries(String)}. Cache + circuit-breaker
     * behavior is intentionally identical to the platform-key path so
     * BYOK calls share the same observability story.
     */
    default List<NewsSummary> generateNewsSummaries(String topic, String overrideApiKey) {
        return generateNewsSummaries(topic);
    }

    default QuizGenerationResult generateQuizQuestions(String digestContent, String overrideApiKey) {
        return generateQuizQuestions(digestContent);
    }

    /**
     * Difficulty hint passed to the quiz generator. Lets the prompt skew toward
     * easier or harder questions when we have signal from the user's recent
     * attempts. Implementations may ignore the hint.
     */
    enum DifficultyHint { EASIER, NORMAL, HARDER }

    default QuizGenerationResult generateQuizQuestions(String digestContent, DifficultyHint hint) {
        return generateQuizQuestions(digestContent);
    }

    /**
     * BYOK + difficulty overload — bill the quiz to {@code overrideApiKey} when the
     * user supplied one, otherwise fall back to the platform-key path. Lets the
     * digest job keep a BYOK user's quiz on their own key (it previously always
     * used the platform key). Implementations should skip the shared cache and the
     * platform circuit breaker on the BYOK path, mirroring the summaries overload.
     */
    default QuizGenerationResult generateQuizQuestions(String digestContent, DifficultyHint hint, String overrideApiKey) {
        if (overrideApiKey == null || overrideApiKey.isBlank()) {
            return generateQuizQuestions(digestContent, hint);
        }
        return generateQuizQuestions(digestContent, overrideApiKey);
    }
}
