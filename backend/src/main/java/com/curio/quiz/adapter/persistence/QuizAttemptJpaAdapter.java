package com.curio.quiz.adapter.persistence;

import com.curio.quiz.entity.QuizAttempt;
import com.curio.quiz.port.out.QuizAttemptPort;
import com.curio.quiz.repository.QuizAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QuizAttemptJpaAdapter implements QuizAttemptPort {

    private final QuizAttemptRepository repository;

    @Override
    public Page<QuizAttempt> findByUserIdWithQuiz(UUID userId, Pageable pageable) {
        return repository.findByUserIdWithQuiz(userId, pageable);
    }

    @Override
    public Optional<QuizAttempt> findByUserIdAndQuizId(UUID userId, UUID quizId) {
        return repository.findByUserIdAndQuizId(userId, quizId);
    }

    @Override
    public Optional<QuizAttempt> findByUserIdAndQuizIdForUpdate(UUID userId, UUID quizId) {
        return repository.findByUserIdAndQuizIdForUpdate(userId, quizId);
    }

    @Override
    public List<QuizAttempt> findTop5ByUserIdOrderByCompletedAtDesc(UUID userId) {
        return repository.findTop5ByUserIdOrderByCompletedAtDesc(userId);
    }

    @Override
    public long countByUserId(UUID userId) {
        return repository.countByUserId(userId);
    }

    @Override
    public long countByCompletedAtAfter(LocalDateTime dateTime) {
        return repository.countByCompletedAtAfter(dateTime);
    }

    @Override
    public Double findAverageScoreByUserId(UUID userId) {
        return repository.findAverageScoreByUserId(userId);
    }

    @Override
    public QuizAttempt save(QuizAttempt attempt) {
        return repository.save(attempt);
    }

    @Override
    public QuizAttempt saveAndFlush(QuizAttempt attempt) {
        return repository.saveAndFlush(attempt);
    }
}
