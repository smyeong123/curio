package com.curio.shared.digest;

import com.curio.news.entity.Digest;
import com.curio.news.port.in.DigestGeneration;
import com.curio.news.port.in.DigestProgressListener;
import com.curio.news.port.in.NewsUseCase;
import com.curio.quiz.port.in.QuizUseCase;
import com.curio.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The one sequence every entry point runs for a user: generate today's digest,
 * then its quiz. The quiz is best-effort — its failure never fails the digest,
 * which is what the reader actually asked for — but it is reported so batches
 * can count it. Used by the scheduled job, the hourly just-in-time send, the
 * admin trigger and the self-serve Studio, so the four never drift apart.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DigestPipeline {

    private final NewsUseCase newsService;
    private final QuizUseCase quizService;

    public enum QuizStatus { GENERATED, FAILED, NOT_ATTEMPTED }

    public record Outcome(DigestGeneration.Status status, Digest digest, QuizStatus quiz) {
        public boolean generated() {
            return status == DigestGeneration.Status.GENERATED;
        }
    }

    public Outcome generateWithQuiz(User user) {
        return generateWithQuiz(user, DigestProgressListener.NOOP);
    }

    public Outcome generateWithQuiz(User user, DigestProgressListener listener) {
        DigestProgressListener progress = listener != null ? listener : DigestProgressListener.NOOP;
        DigestGeneration generation = newsService.generate(user, progress);
        if (!generation.generated()) {
            return new Outcome(generation.status(), null, QuizStatus.NOT_ATTEMPTED);
        }
        Digest digest = generation.digest();
        progress.onQuizStart();
        try {
            boolean saved = quizService.generateQuizForDigest(digest) != null;
            if (!saved) {
                log.warn("Quiz for digest {} (user {}) came back empty — not persisted", digest.getId(), user.getId());
            }
            return new Outcome(generation.status(), digest, saved ? QuizStatus.GENERATED : QuizStatus.FAILED);
        } catch (Exception e) {
            log.warn("Quiz generation failed for digest {} (user {})", digest.getId(), user.getId(), e);
            return new Outcome(generation.status(), digest, QuizStatus.FAILED);
        }
    }
}
