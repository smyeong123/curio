package com.curio.quiz.dto;

import com.curio.quiz.entity.QuizAttempt;

/**
 * The caller's existing attempt on a quiz, attached to {@link QuizResponse} so a
 * revisit shows "your best score" instead of a blank form.
 *
 * @param completedAt ISO local date-time text (no zone), or null when unknown
 */
public record PreviousAttempt(int score, String completedAt) {

    public static PreviousAttempt from(QuizAttempt attempt) {
        return new PreviousAttempt(
                attempt.getScore(),
                attempt.getCompletedAt() != null ? attempt.getCompletedAt().toString() : null);
    }
}
