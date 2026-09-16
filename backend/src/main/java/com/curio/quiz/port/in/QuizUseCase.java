package com.curio.quiz.port.in;

import com.curio.news.entity.Digest;
import com.curio.quiz.dto.QuizResponse;
import com.curio.quiz.dto.QuizSubmitRequest;
import com.curio.quiz.entity.Quiz;
import org.springframework.data.domain.Page;

import java.util.Map;
import java.util.UUID;

/**
 * Inbound port for quiz use cases.
 * Controllers and scheduled jobs (driving adapters) depend on this interface,
 * not on the concrete QuizService implementation.
 */
public interface QuizUseCase {

    QuizResponse getQuizForDigest(UUID digestId, UUID userId);

    Quiz generateQuizForDigest(Digest digest);

    Map<String, Object> submitQuiz(UUID quizId, UUID userId, QuizSubmitRequest request);

    Page<Map<String, Object>> getHistory(UUID userId, int page);
}
