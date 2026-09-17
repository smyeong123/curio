package com.curio.quiz.service;

import com.curio.news.dto.QuizGenerationResult;
import com.curio.news.dto.QuizQuestionItem;
import com.curio.news.entity.Digest;
import com.curio.news.port.out.DigestPort;
import com.curio.news.port.out.AiService;
import com.curio.quiz.dto.QuizHistoryEntry;
import com.curio.quiz.dto.QuizQuestionSet.QuizQuestionView;
import com.curio.quiz.dto.QuizResponse;
import com.curio.quiz.dto.QuizSubmitRequest;
import com.curio.quiz.dto.QuizSubmitResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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

        assertThat(response.getPreviousAttempt().score()).isEqualTo(4);
        assertThat(response.getPreviousAttempt().completedAt()).isEqualTo("2026-07-03T09:30");
    }

    @Test
    void getQuizForDigest_stripsTheAnswerKey_fromStoredQuestions() {
        Quiz existing = Quiz.builder().id(quizId).digest(digest).questions(storedQuiz()).build();
        when(quizPort.findByDigestId(digestId)).thenReturn(Optional.of(existing));

        QuizResponse response = quizService.getQuizForDigest(digestId, userId);

        List<QuizQuestionView> served = response.getQuestions().questions();
        assertThat(served).hasSize(2);
        assertThat(served.get(0).id()).isEqualTo(1);
        assertThat(served.get(0).question()).isEqualTo("Which lab shipped it?");
        assertThat(served.get(0).options()).containsEntry("A", "Anthropic").containsEntry("B", "DeepSeek");
        // The record has no correct/explanation component, so the key can't leak.
        assertThat(objectMapper.convertValue(served.get(0), Map.class))
                .containsOnlyKeys("id", "question", "options");
    }

    @Test
    void getQuizForDigest_servesAnEmptyQuestionList_whenTheStoredDocumentHasNone() {
        Quiz existing = Quiz.builder().id(quizId).digest(digest).questions(Map.of()).build();
        when(quizPort.findByDigestId(digestId)).thenReturn(Optional.of(existing));

        QuizResponse response = quizService.getQuizForDigest(digestId, userId);

        assertThat(response.getQuestions().questions()).isEmpty();
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
                .thenReturn(new QuizAttemptRecorder.Outcome(true, 5));

        QuizSubmitRequest req = new QuizSubmitRequest();
        req.setAnswers(Map.of(1, "A", 2, "B", 3, "C", 4, "D", 5, "A"));

        QuizSubmitResponse result = quizService.submitQuiz(quizId, userId, req);

        assertThat(result.score()).isEqualTo(5);
        assertThat(result.totalQuestions()).isEqualTo(5);
        assertThat(result.bestScore()).isEqualTo(5);
        assertThat(result.improved()).isTrue();
    }

    @Test
    void submitQuiz_scoresPartialAndIncludesPerQuestionFeedback() {
        Quiz quiz = quizWithFiveQuestions();
        when(quizPort.findById(quizId)).thenReturn(Optional.of(quiz));
        when(quizAttemptRecorder.record(eq(userId), eq(quizId), any(), any(), eq(3)))
                .thenReturn(new QuizAttemptRecorder.Outcome(true, 3));

        QuizSubmitRequest req = new QuizSubmitRequest();
        // Three right (1A, 3C, 5A), two wrong.
        req.setAnswers(Map.of(1, "A", 2, "X", 3, "C", 4, "X", 5, "A"));

        QuizSubmitResponse result = quizService.submitQuiz(quizId, userId, req);

        assertThat(result.score()).isEqualTo(3);
        List<QuizSubmitResponse.QuestionResult> results = result.results();
        assertThat(results).hasSize(5);
        assertThat(results.get(0).questionId()).isEqualTo(1);
        assertThat(results.get(0).correct()).isTrue();
        assertThat(results.get(0).correctAnswer()).isEqualTo("A");
        assertThat(results.get(0).explanation()).isEqualTo("Q1");
        assertThat(results.get(1).correct()).isFalse();
        assertThat(results.get(1).correctAnswer()).isEqualTo("B");
    }

    @Test
    void submitQuiz_scoresTheQuizExactlyAsItWasStored_roundTrip() {
        // The quiz goes to the jsonb column as the map form of QuizGenerationResult and
        // is read back through convertValue; scoring must see the same ids and answers.
        Quiz quiz = Quiz.builder().id(quizId).digest(digest).questions(storedQuiz()).build();
        when(quizPort.findById(quizId)).thenReturn(Optional.of(quiz));
        when(quizAttemptRecorder.record(eq(userId), eq(quizId), any(), any(), eq(1)))
                .thenReturn(new QuizAttemptRecorder.Outcome(true, 1));

        QuizSubmitRequest req = new QuizSubmitRequest();
        req.setAnswers(Map.of(1, "A", 2, "A"));

        QuizSubmitResponse result = quizService.submitQuiz(quizId, userId, req);

        assertThat(result.score()).isEqualTo(1);
        assertThat(result.totalQuestions()).isEqualTo(2);
        assertThat(result.results()).hasSize(2);
        assertThat(result.results().get(0).correct()).isTrue();
        assertThat(result.results().get(0).explanation()).isEqualTo("Anthropic shipped it.");
        assertThat(result.results().get(1).correct()).isFalse();
        assertThat(result.results().get(1).correctAnswer()).isEqualTo("C");
    }

    @Test
    void submitQuiz_scoresZeroOfZero_whenTheStoredDocumentHasNoQuestions() {
        Quiz quiz = Quiz.builder().id(quizId).digest(digest).questions(Map.of()).build();
        when(quizPort.findById(quizId)).thenReturn(Optional.of(quiz));
        when(quizAttemptRecorder.record(eq(userId), eq(quizId), any(), any(), eq(0)))
                .thenReturn(new QuizAttemptRecorder.Outcome(true, 0));

        QuizSubmitRequest req = new QuizSubmitRequest();
        req.setAnswers(Map.of());

        QuizSubmitResponse result = quizService.submitQuiz(quizId, userId, req);

        assertThat(result.score()).isZero();
        assertThat(result.totalQuestions()).isZero();
        assertThat(result.results()).isEmpty();
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
                .thenReturn(new QuizAttemptRecorder.Outcome(false, 5));

        QuizSubmitRequest req = new QuizSubmitRequest();
        req.setAnswers(Map.of(1, "A", 2, "X", 3, "C", 4, "X", 5, "A")); // 3/5, lower than prior 5

        QuizSubmitResponse result = quizService.submitQuiz(quizId, userId, req);

        assertThat(result.score()).isEqualTo(3);       // honest live feedback
        assertThat(result.bestScore()).isEqualTo(5);   // record unchanged
        assertThat(result.improved()).isFalse();
    }

    @Test
    void submitQuiz_recoversFromConcurrentFirstSubmit() {
        Quiz quiz = quizWithFiveQuestions();
        when(quizPort.findById(quizId)).thenReturn(Optional.of(quiz));
        // First insert loses the race (unique-constraint violation); recover() succeeds.
        when(quizAttemptRecorder.record(eq(userId), eq(quizId), any(), any(), eq(5)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));
        when(quizAttemptRecorder.recover(eq(userId), eq(quizId), any(), eq(5)))
                .thenReturn(new QuizAttemptRecorder.Outcome(true, 5));

        QuizSubmitRequest req = new QuizSubmitRequest();
        req.setAnswers(Map.of(1, "A", 2, "B", 3, "C", 4, "D", 5, "A")); // 5/5

        QuizSubmitResponse result = quizService.submitQuiz(quizId, userId, req);

        assertThat(result.score()).isEqualTo(5);
        assertThat(result.bestScore()).isEqualTo(5);
        assertThat(result.improved()).isTrue();
        verify(quizAttemptRecorder).recover(eq(userId), eq(quizId), any(), eq(5));
    }

    @Test
    void submitQuiz_surfacesRecorderResult_whenResubmittedHigher() {
        Quiz quiz = quizWithFiveQuestions();
        when(quizPort.findById(quizId)).thenReturn(Optional.of(quiz));
        when(quizAttemptRecorder.record(eq(userId), eq(quizId), any(), any(), eq(5)))
                .thenReturn(new QuizAttemptRecorder.Outcome(true, 5));

        QuizSubmitRequest req = new QuizSubmitRequest();
        req.setAnswers(Map.of(1, "A", 2, "B", 3, "C", 4, "D", 5, "A")); // 5/5, beats a prior lower score

        QuizSubmitResponse result = quizService.submitQuiz(quizId, userId, req);

        assertThat(result.score()).isEqualTo(5);
        assertThat(result.bestScore()).isEqualTo(5);
        assertThat(result.improved()).isTrue();
    }

    // --- getHistory ---

    @Test
    void getHistory_reportsTheStoredQuestionCount_andFallsBackToFive() {
        Quiz twoQuestionQuiz = Quiz.builder().id(quizId).digest(digest).questions(storedQuiz()).build();
        Quiz emptyQuiz = Quiz.builder().id(UUID.randomUUID()).digest(digest).questions(Map.of()).build();
        LocalDateTime completedAt = LocalDateTime.of(2026, 7, 3, 9, 30);
        QuizAttempt first = QuizAttempt.builder().id(UUID.randomUUID()).quiz(twoQuestionQuiz).user(user)
                .score(2).completedAt(completedAt).build();
        QuizAttempt second = QuizAttempt.builder().id(UUID.randomUUID()).quiz(emptyQuiz).user(user)
                .score(4).completedAt(completedAt).build();
        when(quizAttemptPort.findByUserIdWithQuiz(eq(userId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 10), 2));

        Page<QuizHistoryEntry> history = quizService.getHistory(userId, 0);

        assertThat(history.getContent()).hasSize(2);
        QuizHistoryEntry entry = history.getContent().get(0);
        assertThat(entry.id()).isEqualTo(first.getId());
        assertThat(entry.quizId()).isEqualTo(quizId);
        assertThat(entry.score()).isEqualTo(2);
        assertThat(entry.totalQuestions()).isEqualTo(2);
        assertThat(entry.completedAt()).isEqualTo(completedAt);
        assertThat(history.getContent().get(1).totalQuestions()).isEqualTo(5);
    }

    /**
     * A quiz exactly as {@code generateQuizForDigest} persists it: the AI result
     * converted to its map form, answer key included.
     */
    private Map<String, Object> storedQuiz() {
        QuizGenerationResult generated = new QuizGenerationResult(List.of(
                QuizQuestionItem.builder().id(1).question("Which lab shipped it?")
                        .options(Map.of("A", "Anthropic", "B", "DeepSeek"))
                        .correct("A").explanation("Anthropic shipped it.").build(),
                QuizQuestionItem.builder().id(2).question("Context window?")
                        .options(Map.of("A", "8k", "C", "1M"))
                        .correct("C").explanation("One million tokens.").build()));
        return objectMapper.convertValue(generated, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
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
