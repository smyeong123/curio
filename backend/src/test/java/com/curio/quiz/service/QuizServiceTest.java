package com.curio.quiz.service;

import com.curio.news.dto.QuizGenerationResult;
import com.curio.news.dto.QuizQuestionItem;
import com.curio.news.entity.Digest;
import com.curio.news.port.out.DigestPort;
import com.curio.news.service.AiService;
import com.curio.quiz.dto.QuizResponse;
import com.curio.quiz.dto.QuizSubmitRequest;
import com.curio.quiz.entity.Quiz;
import com.curio.quiz.entity.QuizAttempt;
import com.curio.quiz.port.out.QuizAttemptPort;
import com.curio.quiz.port.out.QuizPort;
import com.curio.shared.exception.ResourceNotFoundException;
import com.curio.shared.i18n.Language;
import com.curio.user.entity.User;
import com.curio.user.entity.UserApiKey;
import com.curio.user.port.in.UserApiKeyUseCase;
import com.curio.user.port.out.UserPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
class QuizServiceTest {

    @Mock private QuizPort quizPort;
    @Mock private QuizAttemptPort quizAttemptPort;
    @Mock private DigestPort digestPort;
    @Mock private QuizAttemptRecorder quizAttemptRecorder;
    @Mock private AiService aiService;
    @Mock private UserApiKeyUseCase userApiKeyService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private QuizService quizService;

    private UUID userId;
    private UUID digestId;
    private UUID quizId;
    private User user;
    private Digest digest;

    @BeforeEach
    void setUp() {
        // A no-op transaction manager: TransactionTemplate.execute runs the callback
        // and returns its value (getTransaction/commit are no-ops on the mock), so the
        // split-transaction reads behave exactly as they do at runtime.
        quizService = new QuizService(quizPort, quizAttemptPort, digestPort, quizAttemptRecorder, aiService, userApiKeyService, objectMapper,
                mock(org.springframework.transaction.PlatformTransactionManager.class));

        userId = UUID.randomUUID();
        digestId = UUID.randomUUID();
        quizId = UUID.randomUUID();
        user = User.builder().id(userId).email("test@example.com").build();
        digest = Digest.builder().id(digestId).user(user).content(Map.of("summaries", List.of())).build();
    }

    // --- getQuizForDigest ---

    @Test
    void getQuizForDigest_returnsExistingQuiz_whenOwnerMatches() {
        Quiz existing = Quiz.builder().id(quizId).digest(digest).questions(Map.of("questions", List.of())).build();
        when(quizPort.findByDigestId(digestId)).thenReturn(Optional.of(existing));

        QuizResponse response = quizService.getQuizForDigest(digestId, userId);

        assertThat(response.getId()).isEqualTo(quizId);
        assertThat(response.getDigestId()).isEqualTo(digestId);
        assertThat(response.getPreviousAttempt()).isNull();
        verifyNoInteractions(aiService);
    }

    @Test
    void getQuizForDigest_includesPreviousAttempt_whenUserAlreadyTookIt() {
        Quiz existing = Quiz.builder().id(quizId).digest(digest).questions(Map.of("questions", List.of())).build();
        when(quizPort.findByDigestId(digestId)).thenReturn(Optional.of(existing));
        QuizAttempt attempt = QuizAttempt.builder()
                .id(UUID.randomUUID())
                .quiz(existing)
                .user(user)
                .score(4)
                .completedAt(java.time.LocalDateTime.of(2026, 7, 3, 9, 30))
                .build();
        when(quizAttemptPort.findByUserIdAndQuizId(userId, quizId)).thenReturn(Optional.of(attempt));

        QuizResponse response = quizService.getQuizForDigest(digestId, userId);

        assertThat(response.getPreviousAttempt())
                .containsEntry("score", 4)
                .containsEntry("completedAt", "2026-07-03T09:30");
    }

    @Test
    void getQuizForDigest_throwsNotFound_whenExistingQuizBelongsToOtherUser() {
        User otherUser = User.builder().id(UUID.randomUUID()).build();
        Digest otherDigest = Digest.builder().id(digestId).user(otherUser).build();
        Quiz quiz = Quiz.builder().id(quizId).digest(otherDigest).questions(Map.of()).build();
        when(quizPort.findByDigestId(digestId)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> quizService.getQuizForDigest(digestId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getQuizForDigest_throwsNotFound_whenDigestMissing() {
        when(quizPort.findByDigestId(digestId)).thenReturn(Optional.empty());
        when(digestPort.findById(digestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quizService.getQuizForDigest(digestId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getQuizForDigest_generatesQuiz_whenNoneExistsAndOwnerMatches() {
        when(quizPort.findByDigestId(digestId)).thenReturn(Optional.empty());
        when(digestPort.findById(digestId)).thenReturn(Optional.of(digest));
        when(userApiKeyService.resolveDecryptedKey(any(UUID.class), any(UserApiKey.Provider.class)))
                .thenReturn(Optional.empty());
        when(aiService.generateQuizQuestions(anyString(), any(Language.class), any(AiService.DifficultyHint.class), nullable(String.class)))
                .thenReturn(fiveQuestionResult());
        Quiz saved = Quiz.builder().id(quizId).digest(digest).questions(Map.of("questions", List.of())).build();
        when(quizPort.save(any(Quiz.class))).thenReturn(saved);

        QuizResponse response = quizService.getQuizForDigest(digestId, userId);

        assertThat(response.getId()).isEqualTo(quizId);
        verify(aiService).generateQuizQuestions(anyString(), any(Language.class), any(AiService.DifficultyHint.class), nullable(String.class));
    }

    @Test
    void getQuizForDigest_billsQuizToUsersByokKey_whenPresent() {
        when(quizPort.findByDigestId(digestId)).thenReturn(Optional.empty());
        when(digestPort.findById(digestId)).thenReturn(Optional.of(digest));
        when(userApiKeyService.resolveDecryptedKey(any(UUID.class), any(UserApiKey.Provider.class)))
                .thenReturn(Optional.of("user-byok-key"));
        when(aiService.generateQuizQuestions(anyString(), any(Language.class), any(AiService.DifficultyHint.class), eq("user-byok-key")))
                .thenReturn(fiveQuestionResult());
        Quiz saved = Quiz.builder().id(quizId).digest(digest).questions(Map.of("questions", List.of())).build();
        when(quizPort.save(any(Quiz.class))).thenReturn(saved);

        quizService.getQuizForDigest(digestId, userId);

        // The user's own key is threaded through; the platform-key overloads are never hit.
        verify(aiService).generateQuizQuestions(anyString(), any(Language.class), any(AiService.DifficultyHint.class), eq("user-byok-key"));
        verify(aiService, never()).generateQuizQuestions(anyString());
        verify(aiService, never()).generateQuizQuestions(anyString(), any(AiService.DifficultyHint.class));
    }

    @Test
    void getQuizForDigest_quizzesInTheLanguageTheDigestWasWrittenIn() {
        Digest koreanDigest = Digest.builder().id(digestId).user(user)
                .content(Map.of("language", "ko", "summaries", List.of())).build();
        when(quizPort.findByDigestId(digestId)).thenReturn(Optional.empty());
        when(digestPort.findById(digestId)).thenReturn(Optional.of(koreanDigest));
        when(userApiKeyService.resolveDecryptedKey(any(UUID.class), any(UserApiKey.Provider.class)))
                .thenReturn(Optional.empty());
        when(aiService.generateQuizQuestions(anyString(), eq(Language.KO), any(AiService.DifficultyHint.class), nullable(String.class)))
                .thenReturn(fiveQuestionResult());
        Quiz saved = Quiz.builder().id(quizId).digest(koreanDigest).questions(Map.of("questions", List.of())).build();
        when(quizPort.save(any(Quiz.class))).thenReturn(saved);

        quizService.getQuizForDigest(digestId, userId);

        verify(aiService).generateQuizQuestions(anyString(), eq(Language.KO), any(AiService.DifficultyHint.class), nullable(String.class));
        verify(aiService, never()).generateQuizQuestions(anyString(), eq(Language.EN), any(AiService.DifficultyHint.class), nullable(String.class));
    }

    @Test
    void getQuizForDigest_throwsNotFound_whenGeneratedDigestBelongsToOtherUser() {
        User otherUser = User.builder().id(UUID.randomUUID()).build();
        Digest otherDigest = Digest.builder().id(digestId).user(otherUser).build();
        when(quizPort.findByDigestId(digestId)).thenReturn(Optional.empty());
        when(digestPort.findById(digestId)).thenReturn(Optional.of(otherDigest));

        assertThatThrownBy(() -> quizService.getQuizForDigest(digestId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- submitQuiz ---

    @Test
    void submitQuiz_scoresAllCorrect() {
        Quiz quiz = quizWithFiveQuestions();
        when(quizPort.findById(quizId)).thenReturn(Optional.of(quiz));
        when(quizAttemptRecorder.record(eq(userId), eq(quizId), any(), any(), eq(5)))
                .thenReturn(new QuizAttemptRecorder.Result(true, 5));

        QuizSubmitRequest req = new QuizSubmitRequest();
        req.setAnswers(Map.of(1, "A", 2, "B", 3, "C", 4, "D", 5, "A"));

        Map<String, Object> result = quizService.submitQuiz(quizId, userId, req);

        assertThat(result).containsEntry("score", 5);
        assertThat(result).containsEntry("totalQuestions", 5);
        assertThat(result).containsEntry("bestScore", 5);
        assertThat(result).containsEntry("improved", true);
    }

    @Test
    void submitQuiz_scoresPartialAndIncludesPerQuestionFeedback() {
        Quiz quiz = quizWithFiveQuestions();
        when(quizPort.findById(quizId)).thenReturn(Optional.of(quiz));
        when(quizAttemptRecorder.record(eq(userId), eq(quizId), any(), any(), eq(3)))
                .thenReturn(new QuizAttemptRecorder.Result(true, 3));

        QuizSubmitRequest req = new QuizSubmitRequest();
        // Three right (1A, 3C, 5A), two wrong.
        req.setAnswers(Map.of(1, "A", 2, "X", 3, "C", 4, "X", 5, "A"));

        Map<String, Object> result = quizService.submitQuiz(quizId, userId, req);

        assertThat(result).containsEntry("score", 3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) result.get("results");
        assertThat(results).hasSize(5);
        assertThat(results.get(0)).containsEntry("correct", true);
        assertThat(results.get(1)).containsEntry("correct", false);
    }

    @Test
    void submitQuiz_throwsNotFound_whenQuizMissing() {
        when(quizPort.findById(quizId)).thenReturn(Optional.empty());
        QuizSubmitRequest req = new QuizSubmitRequest();
        req.setAnswers(Map.of());

        assertThatThrownBy(() -> quizService.submitQuiz(quizId, userId, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitQuiz_surfacesLiveScoreButRecorderBestScore_whenResubmittedLower() {
        Quiz quiz = quizWithFiveQuestions();
        when(quizPort.findById(quizId)).thenReturn(Optional.of(quiz));
        // Recorder keeps the better prior score of 5; this attempt only scored 3.
        when(quizAttemptRecorder.record(eq(userId), eq(quizId), any(), any(), eq(3)))
                .thenReturn(new QuizAttemptRecorder.Result(false, 5));

        QuizSubmitRequest req = new QuizSubmitRequest();
        req.setAnswers(Map.of(1, "A", 2, "X", 3, "C", 4, "X", 5, "A")); // 3/5, lower than prior 5

        Map<String, Object> result = quizService.submitQuiz(quizId, userId, req);

        assertThat(result).containsEntry("score", 3);       // honest live feedback
        assertThat(result).containsEntry("bestScore", 5);   // record unchanged
        assertThat(result).containsEntry("improved", false);
    }

    @Test
    void submitQuiz_recoversFromConcurrentFirstSubmit() {
        Quiz quiz = quizWithFiveQuestions();
        when(quizPort.findById(quizId)).thenReturn(Optional.of(quiz));
        // First insert loses the race (unique-constraint violation); recover() succeeds.
        when(quizAttemptRecorder.record(eq(userId), eq(quizId), any(), any(), eq(5)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));
        when(quizAttemptRecorder.recover(eq(userId), eq(quizId), any(), eq(5)))
                .thenReturn(new QuizAttemptRecorder.Result(true, 5));

        QuizSubmitRequest req = new QuizSubmitRequest();
        req.setAnswers(Map.of(1, "A", 2, "B", 3, "C", 4, "D", 5, "A")); // 5/5

        Map<String, Object> result = quizService.submitQuiz(quizId, userId, req);

        assertThat(result).containsEntry("score", 5);
        assertThat(result).containsEntry("bestScore", 5);
        assertThat(result).containsEntry("improved", true);
        verify(quizAttemptRecorder).recover(eq(userId), eq(quizId), any(), eq(5));
    }

    @Test
    void submitQuiz_surfacesRecorderResult_whenResubmittedHigher() {
        Quiz quiz = quizWithFiveQuestions();
        when(quizPort.findById(quizId)).thenReturn(Optional.of(quiz));
        when(quizAttemptRecorder.record(eq(userId), eq(quizId), any(), any(), eq(5)))
                .thenReturn(new QuizAttemptRecorder.Result(true, 5));

        QuizSubmitRequest req = new QuizSubmitRequest();
        req.setAnswers(Map.of(1, "A", 2, "B", 3, "C", 4, "D", 5, "A")); // 5/5, beats a prior lower score

        Map<String, Object> result = quizService.submitQuiz(quizId, userId, req);

        assertThat(result).containsEntry("score", 5);
        assertThat(result).containsEntry("bestScore", 5);
        assertThat(result).containsEntry("improved", true);
    }

    private Quiz quizWithFiveQuestions() {
        List<Map<String, Object>> questions = List.of(
                Map.of("id", 1, "correct", "A", "explanation", "Q1"),
                Map.of("id", 2, "correct", "B", "explanation", "Q2"),
                Map.of("id", 3, "correct", "C", "explanation", "Q3"),
                Map.of("id", 4, "correct", "D", "explanation", "Q4"),
                Map.of("id", 5, "correct", "A", "explanation", "Q5")
        );
        return Quiz.builder().id(quizId).digest(digest).questions(Map.of("questions", questions)).build();
    }

    /** A valid 5-question AI result — passes the "exactly 5 questions" non-empty guard. */
    private QuizGenerationResult fiveQuestionResult() {
        QuizQuestionItem item = QuizQuestionItem.builder()
                .id(1).question("Q").options(Map.of("A", "a", "B", "b", "C", "c", "D", "d"))
                .correct("A").explanation("e").build();
        return new QuizGenerationResult(List.of(item, item, item, item, item));
    }
}
