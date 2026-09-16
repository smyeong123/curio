package com.curio.quiz.port.out;

import com.curio.quiz.entity.Quiz;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for quiz persistence.
 * Domain services depend on this interface; the JPA adapter (QuizRepository)
 * is the infrastructure implementation.
 */
public interface QuizPort {

    Optional<Quiz> findById(UUID id);

    Optional<Quiz> findByDigestId(UUID digestId);

    Quiz save(Quiz quiz);
}
