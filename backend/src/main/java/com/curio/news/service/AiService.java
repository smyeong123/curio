package com.curio.news.service;

import com.curio.news.dto.NewsSummary;
import com.curio.news.dto.QuizGenerationResult;
import com.curio.shared.i18n.Language;

import java.util.List;

public interface AiService {

    /** Use the platform's configured key. English edition. */
    List<NewsSummary> generateNewsSummaries(String topic);

    /** Use the platform's configured key. English edition. */
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

    /**
     * The edition-aware entry point the digest pipeline uses: writes the summaries in
     * {@code language} (English or Korean), billed to {@code overrideApiKey} when the
     * user supplied one, else the platform key. Platform-key results are cached per
     * topic+date+language so the two editions never share a cache entry.
     */
    default List<NewsSummary> generateNewsSummaries(String topic, Language language, String overrideApiKey) {
        return generateNewsSummaries(topic, overrideApiKey);
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

    /**
     * The edition-aware quiz entry point: questions, options and explanations are
     * written in {@code language} — which must be the language the digest itself was
     * written in (see {@link Language#fromDigestContent}), not the user's current setting.
     */
    default QuizGenerationResult generateQuizQuestions(String digestContent, Language language, DifficultyHint hint, String overrideApiKey) {
        return generateQuizQuestions(digestContent, hint, overrideApiKey);
    }
}
