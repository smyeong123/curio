package com.curio.quiz.port.out;

import com.curio.quiz.entity.QuizAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for quiz-attempt persistence.
 * Domain services depend on this interface; the JPA adapter
 * (QuizAttemptRepository) is the infrastructure implementation.
 */
public interface QuizAttemptPort {

    Page<QuizAttempt> findByUserIdWithQuiz(UUID userId, Pageable pageable);

    Optional<QuizAttempt> findByUserIdAndQuizId(UUID userId, UUID quizId);

    /** Same lookup with a pessimistic write lock — callers updating the row must use this. */
    Optional<QuizAttempt> findByUserIdAndQuizIdForUpdate(UUID userId, UUID quizId);

    List<QuizAttempt> findTop5ByUserIdOrderByCompletedAtDesc(UUID userId);

    long countByUserId(UUID userId);

    long countByCompletedAtAfter(LocalDateTime dateTime);

    Double findAverageScoreByUserId(UUID userId);

    QuizAttempt save(QuizAttempt attempt);

    /**
     * Persist and flush immediately so a unique-constraint violation surfaces now
     * (rather than being deferred to transaction commit). Used by the concurrent
     * first-submit recovery path.
     */
    QuizAttempt saveAndFlush(QuizAttempt attempt);
}
