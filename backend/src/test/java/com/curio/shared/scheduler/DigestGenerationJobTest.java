package com.curio.shared.scheduler;

import com.curio.news.entity.Digest;
import com.curio.news.port.in.NewsUseCase;
import com.curio.quiz.port.in.QuizUseCase;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DigestGenerationJobTest {

    @Mock private NewsUseCase newsService;
    @Mock private QuizUseCase quizService;
    @Mock private UserPort userPort;
    @Mock private JobStatusRegistry jobStatusRegistry;
    @Mock private JobFailureNotifier jobFailureNotifier;

    private DigestGenerationJob job;

    @BeforeEach
    void setUp() {
        job = new DigestGenerationJob(newsService, quizService, userPort, jobStatusRegistry,
                jobFailureNotifier, Executors.newSingleThreadExecutor());
    }

    @Test
    void generateDailyDigests_generatesDigestsAndQuizzes() {
        User user = User.builder().id(UUID.randomUUID()).email("test@example.com").build();
        Digest digest = Digest.builder().id(UUID.randomUUID()).user(user).build();

        Page<User> page = new PageImpl<>(List.of(user));
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(page);
        when(newsService.generateDigestForUser(user)).thenReturn(digest);

        job.generateDailyDigests();

        verify(newsService).generateDigestForUser(user);
        verify(quizService).generateQuizForDigest(digest);
        verify(jobStatusRegistry).recordSuccess(eq(DigestGenerationJob.JOB_NAME), any(Map.class));
    }

    @Test
    void generateDailyDigests_continuesOnDigestFailure() {
        User user1 = User.builder().id(UUID.randomUUID()).email("a@test.com").build();
        User user2 = User.builder().id(UUID.randomUUID()).email("b@test.com").build();
        Digest digest2 = Digest.builder().id(UUID.randomUUID()).user(user2).build();

        Page<User> page = new PageImpl<>(List.of(user1, user2));
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(page);
        when(newsService.generateDigestForUser(user1)).thenThrow(new RuntimeException("fail"));
        when(newsService.generateDigestForUser(user2)).thenReturn(digest2);

        job.generateDailyDigests();

        verify(newsService).generateDigestForUser(user1);
        verify(newsService).generateDigestForUser(user2);
        verify(quizService).generateQuizForDigest(digest2);
        verify(jobStatusRegistry).recordSuccess(eq(DigestGenerationJob.JOB_NAME), any(Map.class));
    }
}
