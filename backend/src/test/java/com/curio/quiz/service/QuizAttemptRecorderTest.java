package com.curio.quiz.service;

import com.curio.quiz.entity.Quiz;
import com.curio.quiz.entity.QuizAttempt;
import com.curio.quiz.port.out.QuizAttemptPort;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the "better score wins" upsert logic that lives in the
 * REQUIRES_NEW recorder (extracted from QuizService so a concurrent first submit
 * recovers instead of 500ing).
 */
@ExtendWith(MockitoExtension.class)
class QuizAttemptRecorderTest {

    @Mock private QuizAttemptPort quizAttemptPort;
    @Mock private UserPort userPort;
    @InjectMocks private QuizAttemptRecorder recorder;

    private final UUID userId = UUID.randomUUID();
    private final UUID quizId = UUID.randomUUID();
    private final Map<String, Object> answers = Map.of("answers", Map.of());

    private Quiz quiz() {
        return Quiz.builder().id(quizId).build();
    }

    @Test
    void record_insertsNewAttempt_whenNonePrior() {
        when(quizAttemptPort.findByUserIdAndQuizIdForUpdate(userId, quizId)).thenReturn(Optional.empty());
        when(userPort.getReferenceById(userId)).thenReturn(User.builder().id(userId).build());

        QuizAttemptRecorder.Result r = recorder.record(userId, quizId, quiz(), answers, 4);

        assertThat(r.improved()).isTrue();
        assertThat(r.bestScore()).isEqualTo(4);
        verify(quizAttemptPort).saveAndFlush(any(QuizAttempt.class));
    }

    @Test
    void record_updatesInPlace_whenHigherScore() {
        QuizAttempt prior = QuizAttempt.builder().id(UUID.randomUUID()).score(2).answers(Map.of()).build();
        when(quizAttemptPort.findByUserIdAndQuizIdForUpdate(userId, quizId)).thenReturn(Optional.of(prior));

        QuizAttemptRecorder.Result r = recorder.record(userId, quizId, quiz(), answers, 5);

        assertThat(r.improved()).isTrue();
        assertThat(r.bestScore()).isEqualTo(5);
        assertThat(prior.getScore()).isEqualTo(5);
        verify(quizAttemptPort).save(prior);
        verify(quizAttemptPort, never()).saveAndFlush(any());
    }

    @Test
    void record_keepsPrior_whenLowerOrEqual() {
        QuizAttempt prior = QuizAttempt.builder().id(UUID.randomUUID()).score(5).answers(Map.of()).build();
        when(quizAttemptPort.findByUserIdAndQuizIdForUpdate(userId, quizId)).thenReturn(Optional.of(prior));

        QuizAttemptRecorder.Result r = recorder.record(userId, quizId, quiz(), answers, 3);

        assertThat(r.improved()).isFalse();
        assertThat(r.bestScore()).isEqualTo(5);
        verify(quizAttemptPort, never()).save(any());
        verify(quizAttemptPort, never()).saveAndFlush(any());
    }

    @Test
    void recover_appliesBetterScore_whenRowNowExists() {
        QuizAttempt prior = QuizAttempt.builder().id(UUID.randomUUID()).score(2).answers(Map.of()).build();
        when(quizAttemptPort.findByUserIdAndQuizIdForUpdate(userId, quizId)).thenReturn(Optional.of(prior));

        QuizAttemptRecorder.Result r = recorder.recover(userId, quizId, answers, 4);

        assertThat(r.improved()).isTrue();
        assertThat(r.bestScore()).isEqualTo(4);
        verify(quizAttemptPort).save(prior);
    }
}
