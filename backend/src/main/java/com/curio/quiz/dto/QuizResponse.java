package com.curio.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizResponse {
    private UUID id;
    private UUID digestId;
    private Map<String, Object> questions;
    /**
     * The caller's existing attempt on this quiz ({@code score}, {@code completedAt}),
     * or null if they haven't taken it yet. Lets the client show "your best score"
     * on revisit — retakes are "better score wins" (V25).
     */
    private Map<String, Object> previousAttempt;
}
