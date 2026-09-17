package com.curio.quiz.dto;

import com.curio.news.dto.QuizQuestionItem;

import java.util.List;
import java.util.Map;

/**
 * The questions of a quiz as served to the client: the answer key
 * ({@code correct}, {@code explanation}) is stripped so scoring stays server-side.
 * Wrapped in an object (not a bare array) to match the stored JSON's shape.
 */
public record QuizQuestionSet(List<QuizQuestionView> questions) {

    /** A question without its answer key. */
    public record QuizQuestionView(int id, String question, Map<String, String> options) {

        public static QuizQuestionView from(QuizQuestionItem item) {
            return new QuizQuestionView(item.getId(), item.getQuestion(), item.getOptions());
        }
    }
}
