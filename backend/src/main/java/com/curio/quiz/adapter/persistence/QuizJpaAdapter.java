package com.curio.quiz.adapter.persistence;

import com.curio.quiz.entity.Quiz;
import com.curio.quiz.port.out.QuizPort;
import com.curio.quiz.repository.QuizRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QuizJpaAdapter implements QuizPort {

    private final QuizRepository repository;

    @Override
    public Optional<Quiz> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Optional<Quiz> findByDigestId(UUID digestId) {
        return repository.findByDigestId(digestId);
    }

    @Override
    public Quiz save(Quiz quiz) {
        return repository.save(quiz);
    }
}
