package com.curio.quiz.dto;

import java.util.List;

/**
 * Outcome of {@code POST /api/v1/quiz/{id}/submit}.
 *
 * @param score          what this attempt scored (honest live feedback, even on a worse retake)
 * @param totalQuestions how many questions were scored
 * @param results        per-question verdict, in the quiz's own order
 * @param bestScore      the score now on file for this quiz (retakes are "better score wins")
 * @param improved       whether this attempt replaced the stored record
 */
public record QuizSubmitResponse(
        int score,
        int totalQuestions,
        List<QuestionResult> results,
        int bestScore,
        boolean improved) {

    /** One scored question, with the answer key revealed now that the attempt is in. */
    public record QuestionResult(
            int questionId,
            boolean correct,
            String correctAnswer,
            String explanation) {}
}
