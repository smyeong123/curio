package com.curio.shared.digest;

import com.curio.news.entity.Digest;
import com.curio.news.port.in.DigestGeneration;
import com.curio.news.port.in.DigestProgressListener;
import com.curio.news.port.in.NewsUseCase;
import com.curio.quiz.entity.Quiz;
import com.curio.quiz.port.in.QuizUseCase;
import com.curio.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DigestPipelineTest {

    @Mock private NewsUseCase newsService;
    @Mock private QuizUseCase quizService;
    @InjectMocks private DigestPipeline pipeline;

    private final User user = User.builder().id(UUID.randomUUID()).email("r@example.com").build();
    private final Digest digest = Digest.builder().id(UUID.randomUUID()).user(user).build();

    @Test
    void generatesTheDigest_thenTheQuiz_andReportsBoth() {
        when(newsService.generate(eq(user), any())).thenReturn(DigestGeneration.generated(digest));
        when(quizService.generateQuizForDigest(digest)).thenReturn(Quiz.builder().id(UUID.randomUUID()).build());
        DigestProgressListener listener = mock(DigestProgressListener.class);

        DigestPipeline.Outcome result = pipeline.generateWithQuiz(user, listener);

        assertThat(result.generated()).isTrue();
        assertThat(result.digest()).isSameAs(digest);
        assertThat(result.quiz()).isEqualTo(DigestPipeline.QuizStatus.GENERATED);
        verify(listener).onQuizStart();
    }

    @Test
    void aQuizFailure_neverFailsTheDigest() {
        when(newsService.generate(eq(user), any())).thenReturn(DigestGeneration.generated(digest));
        when(quizService.generateQuizForDigest(digest)).thenThrow(new RuntimeException("provider down"));

        DigestPipeline.Outcome result = pipeline.generateWithQuiz(user);

        assertThat(result.generated()).isTrue();
        assertThat(result.quiz()).isEqualTo(DigestPipeline.QuizStatus.FAILED);
    }

    @Test
    void anEmptyQuiz_isReportedAsFailed() {
        when(newsService.generate(eq(user), any())).thenReturn(DigestGeneration.generated(digest));
        when(quizService.generateQuizForDigest(digest)).thenReturn(null);

        assertThat(pipeline.generateWithQuiz(user).quiz()).isEqualTo(DigestPipeline.QuizStatus.FAILED);
    }

    @Test
    void skipsTheQuiz_whenNothingWasGenerated() {
        when(newsService.generate(eq(user), any())).thenReturn(DigestGeneration.alreadyExists());

        DigestPipeline.Outcome result = pipeline.generateWithQuiz(user);

        assertThat(result.generated()).isFalse();
        assertThat(result.status()).isEqualTo(DigestGeneration.Status.ALREADY_EXISTS);
        assertThat(result.quiz()).isEqualTo(DigestPipeline.QuizStatus.NOT_ATTEMPTED);
        verifyNoInteractions(quizService);
    }
}
