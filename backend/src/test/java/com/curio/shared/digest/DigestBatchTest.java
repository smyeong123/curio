package com.curio.shared.digest;

import com.curio.news.entity.Digest;
import com.curio.news.port.in.DigestGeneration;
import com.curio.shared.batch.InlinePool;
import com.curio.shared.batch.SubscriberBatch;
import com.curio.shared.jobs.JobFailureNotifier;
import com.curio.shared.jobs.JobRunRecorder;
import com.curio.shared.jobs.JobStatusRegistry;
import com.curio.user.entity.User;
import com.curio.user.entity.UserPreferences;
import com.curio.user.port.out.UserPort;
import com.curio.user.port.out.UserPreferencesPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DigestBatchTest {

    @Mock private UserPort userPort;
    @Mock private DigestPipeline pipeline;
    @Mock private UserPreferencesPort preferencesPort;
    @Mock private JobStatusRegistry jobStatusRegistry;
    @Mock private JobFailureNotifier jobFailureNotifier;

    private DigestBatch batch;

    @BeforeEach
    void setUp() {
        batch = new DigestBatch(new SubscriberBatch(userPort), pipeline, preferencesPort,
                new JobRunRecorder(jobStatusRegistry, jobFailureNotifier), InlinePool.inline());
    }

    private static User user(String email) {
        return User.builder().id(UUID.randomUUID()).email(email).build();
    }

    private static DigestPipeline.Outcome generated(User user, DigestPipeline.QuizStatus quiz) {
        Digest digest = Digest.builder().id(UUID.randomUUID()).user(user).build();
        return new DigestPipeline.Outcome(DigestGeneration.Status.GENERATED, digest, quiz);
    }

    private static DigestPipeline.Outcome notGenerated(DigestGeneration.Status status) {
        return new DigestPipeline.Outcome(status, null, DigestPipeline.QuizStatus.NOT_ATTEMPTED);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> recorded() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(jobStatusRegistry).recordSuccess(eq(DigestBatch.JOB_NAME), captor.capture());
        return captor.getValue();
    }

    @Test
    void generatesDigestsAndQuizzes_forEverySubscriber() {
        User a = user("a@example.com");
        User b = user("b@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(a, b)));
        when(pipeline.generateWithQuiz(a)).thenReturn(generated(a, DigestPipeline.QuizStatus.GENERATED));
        when(pipeline.generateWithQuiz(b)).thenReturn(generated(b, DigestPipeline.QuizStatus.FAILED));

        Map<String, Object> result = batch.runForAll();

        assertThat(result).containsEntry("digestSuccess", 2).containsEntry("digestFail", 0)
                .containsEntry("digestSkipped", 0).containsEntry("quizSuccess", 1).containsEntry("quizFail", 1)
                .containsEntry("usersProcessed", 2).containsEntry("usersScanned", 2L)
                .doesNotContainKey("topicFilter").doesNotContainKey("sampleErrors");
        assertThat(recorded()).isEqualTo(result);
        verify(jobFailureNotifier).recordPartialFailure(DigestBatch.JOB_NAME, 2, 0);
    }

    @Test
    void continuesPastFailures_andSkipsUsersWithNothingToGenerate() {
        User fails = user("fail@example.com");
        User has = user("has@example.com");
        User empty = user("empty@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(fails, has, empty)));
        when(pipeline.generateWithQuiz(fails)).thenThrow(new RuntimeException("provider down"));
        when(pipeline.generateWithQuiz(has)).thenReturn(notGenerated(DigestGeneration.Status.ALREADY_EXISTS));
        when(pipeline.generateWithQuiz(empty)).thenReturn(notGenerated(DigestGeneration.Status.NOTHING_GENERATED));

        Map<String, Object> result = batch.runForAll();

        assertThat(result).containsEntry("digestSuccess", 0).containsEntry("digestFail", 1).containsEntry("digestSkipped", 2);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> samples = (List<Map<String, String>>) result.get("sampleErrors");
        assertThat(samples).extracting(s -> s.get("userEmail")).containsExactly("fail@example.com");
        assertThat(samples.get(0)).containsEntry("message", "RuntimeException: provider down");
        verify(jobFailureNotifier).recordPartialFailure(DigestBatch.JOB_NAME, 1, 1);
    }

    @Test
    void aQuietNewsDay_isNotAPartialFailure() {
        User a = user("a@example.com");
        User b = user("b@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(a, b)));
        when(pipeline.generateWithQuiz(any())).thenReturn(notGenerated(DigestGeneration.Status.NOTHING_GENERATED));

        Map<String, Object> result = batch.runForAll();

        assertThat(result).containsEntry("digestFail", 0).containsEntry("digestSkipped", 2)
                .doesNotContainKey("sampleErrors").doesNotContainKey("errorsByType");
        verify(jobFailureNotifier).recordPartialFailure(DigestBatch.JOB_NAME, 0, 0);
    }

    @Test
    void topicFilter_onlyRunsSubscribersOfThoseTopics_andIsRecorded() {
        User claude = user("claude@example.com");
        User other = user("other@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(claude, other)));
        when(preferencesPort.findByUserId(claude.getId()))
                .thenReturn(Optional.of(UserPreferences.builder().topics(new String[]{"Claude (Anthropic)"}).build()));
        when(preferencesPort.findByUserId(other.getId()))
                .thenReturn(Optional.of(UserPreferences.builder().topics(new String[]{"DeepSeek"}).build()));
        when(pipeline.generateWithQuiz(claude)).thenReturn(generated(claude, DigestPipeline.QuizStatus.GENERATED));

        Map<String, Object> result = batch.run(List.of("Claude (Anthropic)"));

        verify(pipeline, never()).generateWithQuiz(other);
        assertThat(result).containsEntry("digestSuccess", 1).containsEntry("usersProcessed", 1)
                .containsEntry("usersScanned", 2L).containsEntry("topicFilter", List.of("Claude (Anthropic)"));
    }

    @Test
    void aRunThatDies_isRecordedAsFailed_andRethrown() {
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(batch::runForAll).isInstanceOf(IllegalStateException.class).hasMessage("db down");

        verify(jobStatusRegistry).recordFailure(DigestBatch.JOB_NAME, "IllegalStateException: db down");
        verify(jobFailureNotifier).recordFailure(eq(DigestBatch.JOB_NAME), any(IllegalStateException.class));
        verify(jobStatusRegistry, never()).recordSuccess(any(), any());
    }
}
