package com.curio.news.port.out;

import com.curio.news.dto.NewsSummary;
import com.curio.news.dto.QuizGenerationResult;
import com.curio.shared.i18n.Language;

import java.util.List;

/**
 * Outbound port to the AI provider (Claude, Gemini or OpenAI, chosen by
 * {@code AI_PROVIDER}). Both operations are edition-aware and BYOK-aware: a
 * non-blank {@code overrideApiKey} bills the call to the user's own key and
 * skips the shared cache and circuit breaker; null/blank uses the platform key.
 */
public interface AiService {

    /**
     * 2-3 plain-language summaries for a topic, written in {@code language}.
     * Platform-key results are cached per topic+date+language and single-flighted
     * so the two editions never share a cache entry.
     */
    List<NewsSummary> generateNewsSummaries(String topic, Language language, String overrideApiKey);

    /**
     * Difficulty hint for the quiz, from the user's recent scores. Skews the
     * question mix; {@link #NORMAL} when there is no history yet.
     */
    enum DifficultyHint { EASIER, NORMAL, HARDER }

    /**
     * A 5-question quiz over {@code digestContent}, written in {@code language} —
     * which must be the language the digest itself was written in (see
     * {@link Language#fromDigestContent}), not the user's current setting.
     */
    QuizGenerationResult generateQuizQuestions(String digestContent, Language language, DifficultyHint hint, String overrideApiKey);

    /** English edition, platform key. */
    default List<NewsSummary> generateNewsSummaries(String topic) {
        return generateNewsSummaries(topic, Language.EN, null);
    }

    /** English edition, platform key, normal difficulty. */
    default QuizGenerationResult generateQuizQuestions(String digestContent) {
        return generateQuizQuestions(digestContent, Language.EN, DifficultyHint.NORMAL, null);
    }
}
