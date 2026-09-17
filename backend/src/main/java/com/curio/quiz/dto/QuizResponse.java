package com.curio.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizResponse {
    private UUID id;
    private UUID digestId;
    /** Questions with the answer key stripped; null only when the quiz has no stored questions. */
    private QuizQuestionSet questions;
    /**
     * The caller's existing attempt on this quiz, or null if they haven't taken it
     * yet. Lets the client show "your best score" on revisit — retakes are "better
     * score wins" (V25).
     */
    private PreviousAttempt previousAttempt;
}
