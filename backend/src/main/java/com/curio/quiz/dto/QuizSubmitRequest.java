package com.curio.quiz.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class QuizSubmitRequest {

    @NotNull(message = "Answers are required")
    private Map<Integer, String> answers;
}
