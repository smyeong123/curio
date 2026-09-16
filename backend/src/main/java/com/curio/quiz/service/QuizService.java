package com.curio.quiz.service;

import com.curio.quiz.dto.QuizSubmitRequest;
import com.curio.quiz.dto.QuizResponse;
import com.curio.news.entity.Digest;
import com.curio.quiz.entity.Quiz;
import com.curio.quiz.entity.QuizAttempt;
import com.curio.quiz.port.in.QuizUseCase;
import com.curio.quiz.port.out.QuizPort;
import com.curio.quiz.port.out.QuizAttemptPort;
import com.curio.news.port.out.DigestPort;
import com.curio.user.entity.UserApiKey;
import com.curio.user.port.in.UserApiKeyUseCase;
import com.curio.shared.exception.ResourceNotFoundException;
import com.curio.news.dto.QuizGenerationResult;
import com.curio.news.dto.QuizQuestionItem;
import com.curio.news.service.AiService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizService implements QuizUseCase {

    private final QuizPort quizPort;
    private final QuizAttemptPort quizAttemptPort;
    private final DigestPort digestPort;
    /** Records the attempt in its own transaction so a concurrent first submit recovers cleanly. */
    private final QuizAttemptRecorder quizAttemptRecorder;
    private final AiService aiService;
    /** Resolves the user's BYOK key so their quiz is billed to them, not the platform. */
    private final UserApiKeyUseCase userApiKeyService;
    private final ObjectMapper objectMapper;
    /** Drives the short read transactions that bracket the no-transaction AI call. */
    private final PlatformTransactionManager transactionManager;

    @Value("${ai.provider:claude}")
    private String platformProvider;

    /**
     * Fetch (or lazily generate) the quiz for a digest. Deliberately NOT
     * {@code @Transactional}: the quiz-generation AI call (~90-120s with retries)
     * must never run while an active transaction pins a pooled JDBC connection. On
     * the on-demand path a self-invoked generate would otherwise inherit this
     * method's transaction, defeating the protective design on
     * {@link #generateQuizForDigest}. The DB work is split into short read
     * transactions ({@link #inReadTx}); the AI call runs between them with no
     * transaction active, mirroring
     * {@link com.curio.news.service.NewsService#generateDigestForUser}.
     */
    public QuizResponse getQuizForDigest(UUID digestId, UUID userId) {
        // Fast path: a quiz already exists. Load + answer-strip inside a short read
        // tx so its lazy digest/user relations initialize (ownership is IDOR-guarded).
        QuizResponse existing = inReadTx(() -> loadExistingQuizResponse(digestId, userId));
        if (existing != null) {
            return existing;
        }

        // No quiz yet — verify ownership and pull the digest in a short read tx.
        Digest digest = inReadTx(() -> loadOwnedDigestForGeneration(digestId, userId));

        // Generate with NO transaction active (the long AI call).
        Quiz quiz;
        try {
            quiz = generateQuizForDigest(digest);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Race: another request persisted the quiz concurrently. Re-read it.
            log.info("Quiz was concurrently generated for digest {}, retrying lookup", digestId);
            return inReadTx(() -> {
                Quiz found = quizPort.findByDigestId(digestId)
                        .orElseThrow(() -> new ResourceNotFoundException("Quiz not found for this digest"));
                return toQuizResponse(found, userId);
            });
        }

        if (quiz == null) {
            // Empty AI result — never persisted (see generateQuizForDigest). Surface a
            // failure so a later request can retry instead of caching a dead quiz.
            throw new ResourceNotFoundException("Failed to generate quiz for this digest");
        }

        final Quiz generated = quiz;
        return inReadTx(() -> toQuizResponse(generated, userId));
    }

    /**
     * Load an already-generated quiz as a client-safe response (answer key stripped,
     * previousAttempt attached), or {@code null} when no quiz exists yet for the digest.
     * Must run inside a transaction — the quiz's digest/user relations are lazy. Throws
     * {@link ResourceNotFoundException} if a quiz exists but is not owned by the caller,
     * mirroring the not-found response elsewhere so quiz existence isn't leaked (IDOR).
     */
    private QuizResponse loadExistingQuizResponse(UUID digestId, UUID userId) {
        Optional<Quiz> existingQuiz = quizPort.findByDigestId(digestId);
        if (existingQuiz.isEmpty()) {
            return null;
        }
        Quiz quiz = existingQuiz.get();
        if (!quiz.getDigest().getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Quiz not found for this digest");
        }
        return toQuizResponse(quiz, userId);
    }

    /**
     * Verify the digest exists and is owned by the caller, returning it for generation.
     * Must run inside a transaction so the lazy user relation resolves; the returned
     * (detached) digest carries its user id for the subsequent no-transaction AI call.
     */
    private Digest loadOwnedDigestForGeneration(UUID digestId, UUID userId) {
        Digest digest = digestPort.findById(digestId)
                .orElseThrow(() -> new ResourceNotFoundException("Digest not found"));
        if (!digest.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Quiz not found for this digest");
        }
        return digest;
    }

    /** Run {@code work} in a short read-only transaction so lazy relations initialize. */
    private <T> T inReadTx(java.util.function.Supplier<T> work) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        return tx.execute(status -> work.get());
    }

    /**
     * Intentionally NOT @Transactional. The quiz-generation AI call (~90s) must not
     * run while holding a pooled JDBC connection — the digest job generates quizzes
     * 500-wide and would exhaust the pool. The digest passed in already carries its
     * user/content in memory; the lookup and save use short repository transactions.
     * (The on-demand getQuizForDigest path also calls this with no transaction
     * active — see that method's split-transaction design.)
     */
    public Quiz generateQuizForDigest(Digest digest) {
        // Check if quiz already exists
        Optional<Quiz> existing = quizPort.findByDigestId(digest.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        // Serialize digest content to string for the AI prompt
        String contentStr;
        try {
            contentStr = objectMapper.writeValueAsString(digest.getContent());
        } catch (Exception e) {
            log.error("Failed to serialize digest content", e);
            return null;
        }

        UUID userId = digest.getUser().getId();
        AiService.DifficultyHint hint = difficultyHintForUser(userId);
        // BYOK: bill the quiz to the user's own key when they have one, so the platform
        // key is never used for a BYOK user (their digest already runs on their key).
        String userKey = resolveUserApiKey(userId);
        QuizGenerationResult quizResult = aiService.generateQuizQuestions(contentStr, hint, userKey);
        // Guard the documented "exactly 5 questions" invariant: never persist an empty
        // quiz. Returning null routes into getQuizForDigest's null-handling (surfacing a
        // "Failed to generate" error) so a later request can retry instead of caching a
        // dead quiz. Minor count variance (3-4 questions) is tolerated — only warned.
        if (quizResult == null || quizResult.getQuestions() == null || quizResult.getQuestions().isEmpty()) {
            log.warn("AI returned an empty quiz for digest {} — not persisting", digest.getId());
            return null;
        }
        if (quizResult.getQuestions().size() != 5) {
            log.warn("Quiz for digest {} has {} questions (expected 5)",
                    digest.getId(), quizResult.getQuestions().size());
        }
        // The AI occasionally emits missing or duplicate question ids. Because
        // QuizQuestionItem.id is a primitive int, those collapse to 0/duplicates and
        // submit-time scoring (which resolves each answer by question id) would read
        // the wrong question. Re-index 1..N deterministically when ids aren't distinct.
        List<QuizQuestionItem> items = quizResult.getQuestions();
        long distinctIds = items.stream().mapToInt(QuizQuestionItem::getId).distinct().count();
        if (distinctIds != items.size()) {
            log.warn("Quiz for digest {} had non-distinct question ids — re-indexing 1..{}",
                    digest.getId(), items.size());
            for (int i = 0; i < items.size(); i++) {
                items.get(i).setId(i + 1);
            }
        }
        Map<String, Object> questions = objectMapper.convertValue(quizResult, new TypeReference<Map<String, Object>>() {});

        Quiz quiz = Quiz.builder()
                .digest(digest)
                .questions(questions)
                .build();

        return quizPort.save(quiz);
    }

    /**
     * Resolve the user's BYOK key for the platform's configured provider so their
     * quiz is billed to their own key. Mirrors {@code NewsService.resolveUserApiKey}.
     * Returns null (→ fall back to the platform key) if they have no validated key.
     * Never logs the key; failures degrade silently to the platform key.
     */
    private String resolveUserApiKey(UUID userId) {
        try {
            return userApiKeyService
                    .resolveDecryptedKey(userId, UserApiKey.Provider.fromConfigName(platformProvider))
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Could not resolve BYOK key for user {} — falling back to platform key", userId);
            return null;
        }
    }

    @Transactional
    public Map<String, Object> submitQuiz(UUID quizId, UUID userId, QuizSubmitRequest request) {
        Quiz quiz = quizPort.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));

        // Ownership check: a quiz belongs to its digest's owner. Without this, any
        // authenticated user could submit against (and read back the answer key of)
        // any quiz id (IDOR). Mirror the not-found response used elsewhere to avoid
        // leaking existence.
        if (quiz.getDigest() == null
                || !quiz.getDigest().getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Quiz not found");
        }

        // Score the quiz
        Map<String, Object> questions = quiz.getQuestions();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questionList = (List<Map<String, Object>>) questions.get("questions");

        int score = 0;
        int total = questionList != null ? questionList.size() : 0;
        List<Map<String, Object>> results = new ArrayList<>();

        if (questionList != null) {
            for (Map<String, Object> q : questionList) {
                int qId = ((Number) q.get("id")).intValue();
                String correct = (String) q.get("correct");
                String userAnswer = request.getAnswers().get(qId);

                boolean isCorrect = correct != null && correct.equals(userAnswer);
                if (isCorrect) score++;

                results.add(Map.of(
                        "questionId", qId,
                        "correct", isCorrect,
                        "correctAnswer", correct != null ? correct : "",
                        "explanation", q.getOrDefault("explanation", "")
                ));
            }
        }

        // Record the attempt with a "better score wins" strategy: re-taking a
        // quiz never creates a duplicate attempt (each quiz stays distinct in the
        // user's history); only a strictly higher score replaces the stored one.
        // A unique constraint on (user_id, quiz_id) backstops this at the DB (V25).
        // Delegate the write to a separate bean whose REQUIRES_NEW transaction isolates
        // the (user_id, quiz_id) unique-constraint violation, so a concurrent first
        // submit recovers as a normal "better score wins" update instead of a 500.
        Map<String, Object> answers = Map.of("answers", request.getAnswers());
        QuizAttemptRecorder.Result outcome;
        try {
            outcome = quizAttemptRecorder.record(userId, quizId, quiz, answers, score);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.info("Concurrent first submit for user {} quiz {} — recovering as update", userId, quizId);
            outcome = quizAttemptRecorder.recover(userId, quizId, answers, score);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("score", score);                    // this attempt's score (honest live feedback)
        response.put("totalQuestions", total);
        response.put("results", results);
        response.put("bestScore", outcome.bestScore());  // the score now stored for this quiz
        response.put("improved", outcome.improved());    // whether this attempt updated the record
        return response;
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> getHistory(UUID userId, int page) {
        Page<QuizAttempt> attempts = quizAttemptPort.findByUserIdWithQuiz(
                userId, PageRequest.of(page, 10));

        return attempts.map(a -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", a.getId());
            entry.put("quizId", a.getQuiz().getId());
            entry.put("score", a.getScore());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questionList = a.getQuiz().getQuestions() != null
                    ? (List<Map<String, Object>>) a.getQuiz().getQuestions().get("questions")
                    : null;
            entry.put("totalQuestions", questionList != null ? questionList.size() : 5);

            entry.put("completedAt", a.getCompletedAt());
            return entry;
        });
    }

    private QuizResponse toQuizResponse(Quiz quiz, UUID userId) {
        // Surface the caller's existing attempt (if any) so a revisit shows
        // "your best score" instead of a blank form. Retakes are better-score-wins.
        Map<String, Object> previousAttempt = quizAttemptPort
                .findByUserIdAndQuizId(userId, quiz.getId())
                .map(attempt -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
                    m.put("score", attempt.getScore());
                    m.put("completedAt", attempt.getCompletedAt() != null
                            ? attempt.getCompletedAt().toString() : null);
                    return (Map<String, Object>) m;
                })
                .orElse(null);
        return QuizResponse.builder()
                .id(quiz.getId())
                .digestId(quiz.getDigest().getId())
                .questions(sanitizeQuestions(quiz.getQuestions()))
                .previousAttempt(previousAttempt)
                .build();
    }

    /**
     * Strip the answer key from a quiz before it is served on the fetch path.
     * The client must not receive {@code correct}/{@code explanation} before
     * submission — scoring is entirely server-side (see {@link #submitQuiz}),
     * which reads straight off the entity and is unaffected by this copy.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeQuestions(Map<String, Object> questions) {
        if (questions == null) return null;
        List<Map<String, Object>> list = (List<Map<String, Object>>) questions.get("questions");
        if (list == null) return Map.of("questions", List.of());
        List<Map<String, Object>> safe = new ArrayList<>();
        for (Map<String, Object> item : list) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", item.get("id"));
            s.put("question", item.get("question"));
            s.put("options", item.get("options"));
            safe.add(s);
        }
        return Map.of("questions", safe);
    }

    /**
     * Coarse difficulty signal from the rolling average of the user's submitted
     * quizzes. Thresholds are deliberately wide — we'd rather drift slowly than
     * thrash. NORMAL when there is no history yet.
     */
    private AiService.DifficultyHint difficultyHintForUser(java.util.UUID userId) {
        if (userId == null) return AiService.DifficultyHint.NORMAL;
        Double avg = quizAttemptPort.findAverageScoreByUserId(userId);
        if (avg == null) return AiService.DifficultyHint.NORMAL;
        // avg is a raw 0-5 correct-answer average (quizzes are invariantly 5 questions),
        // so the fraction thresholds are scaled to that domain: 4.25 = 85% of 5,
        // 2.25 = 45% of 5. Comparing against 0.85 / 0.45 would classify almost every
        // user as HARDER forever.
        if (avg >= 4.25) return AiService.DifficultyHint.HARDER;
        if (avg <= 2.25) return AiService.DifficultyHint.EASIER;
        return AiService.DifficultyHint.NORMAL;
    }
}
