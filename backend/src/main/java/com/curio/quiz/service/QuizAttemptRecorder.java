package com.curio.quiz.service;

import com.curio.quiz.entity.Quiz;
import com.curio.quiz.entity.QuizAttempt;
import com.curio.quiz.port.out.QuizAttemptPort;
import com.curio.user.port.out.UserPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Isolates the quiz-attempt "better score wins" upsert in its own transaction so a
 * first-time concurrent submit for the same (user, quiz) — a double-click, a retry,
 * or two open tabs — that trips the {@code uq_quiz_attempts_user_quiz} unique
 * constraint is recovered as a normal update instead of bubbling up as an HTTP 500.
 *
 * <p>This is a distinct injected bean on purpose: calling it from {@code QuizService}
 * via {@code this} would be a self-invocation that bypasses the Spring proxy and
 * defeats {@link Propagation#REQUIRES_NEW}. The insert is {@code saveAndFlush}ed so the
 * constraint violation happens (and rolls back) inside {@link #record}'s own
 * transaction; the caller then re-reads and updates in the separate transaction of
 * {@link #recover}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuizAttemptRecorder {

    private final QuizAttemptPort quizAttemptPort;
    private final UserPort userPort;

    /** Outcome of recording an attempt: whether the stored record changed, and the score now on file. */
    public record Result(boolean improved, int bestScore) {}

    /**
     * Record the attempt in a fresh transaction. If no prior attempt exists this
     * inserts and flushes (which may throw {@link org.springframework.dao.DataIntegrityViolationException}
     * on a concurrent first submit — the caller recovers via {@link #recover}).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result record(UUID userId, UUID quizId, Quiz quiz,
                         Map<String, Object> answers, int score) {
        QuizAttempt existing = quizAttemptPort.findByUserIdAndQuizIdForUpdate(userId, quizId).orElse(null);
        if (existing != null) {
            return applyBetterScore(existing, answers, score);
        }
        QuizAttempt attempt = QuizAttempt.builder()
                .quiz(quiz)
                .user(userPort.getReferenceById(userId))
                .answers(answers)
                .score(score)
                .build();
        quizAttemptPort.saveAndFlush(attempt);
        return new Result(true, score);
    }

    /**
     * Recovery for the lost insert race: in a fresh transaction re-read the attempt
     * the winning request committed and apply the "better score wins" update.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result recover(UUID userId, UUID quizId, Map<String, Object> answers, int score) {
        QuizAttempt existing = quizAttemptPort.findByUserIdAndQuizIdForUpdate(userId, quizId).orElse(null);
        if (existing == null) {
            // Extremely unlikely (the constraint fired because a row exists) — treat this
            // attempt as authoritative rather than failing the user's submission.
            return new Result(true, score);
        }
        return applyBetterScore(existing, answers, score);
    }

    private Result applyBetterScore(QuizAttempt existing, Map<String, Object> answers, int score) {
        if (score > existing.getScore()) {
            existing.setScore(score);
            existing.setAnswers(answers);
            existing.setCompletedAt(LocalDateTime.now());
            quizAttemptPort.save(existing);
            return new Result(true, score);
        }
        // Keep the better (or equal) prior attempt untouched — no duplicate row.
        return new Result(false, existing.getScore());
    }
}
