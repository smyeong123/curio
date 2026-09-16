package com.curio.shared.scheduler;

import com.curio.news.entity.Digest;
import com.curio.news.port.in.NewsUseCase;
import com.curio.quiz.port.in.QuizUseCase;
import com.curio.shared.exception.RootCauses;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class DigestGenerationJob {

    public static final String JOB_NAME = "digest-generation";
    private static final int CHUNK_SIZE = 500;

    private final NewsUseCase newsService;
    private final QuizUseCase quizService;
    private final UserPort userPort;
    private final JobStatusRegistry jobStatusRegistry;
    private final JobFailureNotifier jobFailureNotifier;
    private final Executor digestExecutor;

    public DigestGenerationJob(
            NewsUseCase newsService,
            QuizUseCase quizService,
            UserPort userPort,
            JobStatusRegistry jobStatusRegistry,
            JobFailureNotifier jobFailureNotifier,
            @Qualifier("digestExecutor") Executor digestExecutor) {
        this.newsService = newsService;
        this.quizService = quizService;
        this.userPort = userPort;
        this.jobStatusRegistry = jobStatusRegistry;
        this.jobFailureNotifier = jobFailureNotifier;
        this.digestExecutor = digestExecutor;
    }

    /**
     * Runs daily at 6:00 AM UTC. Processes users in paged chunks of {@value CHUNK_SIZE}
     * to bound heap usage and give the executor a chance to drain between chunks.
     */
    @Scheduled(cron = "0 0 6 * * *", zone = "UTC")
    @SchedulerLock(name = "digest-generation", lockAtLeastFor = "PT5M", lockAtMostFor = "PT2H")
    public void generateDailyDigests() {
        log.info("Starting daily digest generation...");
        long start = System.currentTimeMillis();

        AtomicInteger digestSuccess = new AtomicInteger();
        AtomicInteger digestFail = new AtomicInteger();
        AtomicInteger quizSuccess = new AtomicInteger();
        AtomicInteger quizFail = new AtomicInteger();

        // Thread-safe because processUser runs on digestExecutor.
        List<Map<String, String>> sampleErrors = new CopyOnWriteArrayList<>();
        ConcurrentHashMap<String, Integer> errorsByType = new ConcurrentHashMap<>();
        final int sampleLimit = 5;

        int pageIndex = 0;
        long totalProcessed = 0;
        Page<User> page;

        do {
            Pageable pageable = PageRequest.of(pageIndex, CHUNK_SIZE, Sort.by("id"));
            page = userPort.findByDeliveryEnabledTrue(pageable);
            List<User> chunk = page.getContent();
            if (chunk.isEmpty()) {
                break;
            }

            log.info("Digest chunk {} ({} users, total so far {})", pageIndex, chunk.size(), totalProcessed);

            List<CompletableFuture<Void>> futures = new ArrayList<>(chunk.size());
            for (User user : chunk) {
                // orTimeout doesn't cancel the running task, so both the timeout handler
                // and processUser's own counting can reach the counters for the same user.
                // First completion wins the count; the loser only logs.
                AtomicBoolean counted = new AtomicBoolean(false);
                futures.add(CompletableFuture.runAsync(
                        () -> processUser(user, counted, digestSuccess, digestFail, quizSuccess, quizFail,
                                sampleErrors, errorsByType, sampleLimit),
                        digestExecutor)
                        .orTimeout(10, TimeUnit.MINUTES)
                        // orTimeout completes the future outside processUser's own error
                        // handling — without this, a timed-out user is counted nowhere.
                        .exceptionally(ex -> {
                            if (counted.compareAndSet(false, true)) {
                                digestFail.incrementAndGet();
                                log.error("Digest task timed out or failed for user {}", user.getId(), ex);
                            }
                            return null;
                        }));
            }

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(30, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.error("Digest chunk {} timed out or was interrupted", pageIndex, e);
                jobFailureNotifier.recordFailure(JOB_NAME + ":chunk-" + pageIndex, e);
            }

            totalProcessed += chunk.size();
            pageIndex++;
        } while (page.hasNext());

        long duration = System.currentTimeMillis() - start;
        log.info("Digest generation completed in {}ms across {} users in {} chunks: {} digests ({} failed), {} quizzes ({} failed)",
                duration, totalProcessed, pageIndex, digestSuccess.get(), digestFail.get(), quizSuccess.get(), quizFail.get());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durationMs", duration);
        result.put("usersProcessed", totalProcessed);
        result.put("chunks", pageIndex);
        result.put("chunkSize", CHUNK_SIZE);
        result.put("digestSuccess", digestSuccess.get());
        result.put("digestFail", digestFail.get());
        result.put("quizSuccess", quizSuccess.get());
        result.put("quizFail", quizFail.get());
        if (!sampleErrors.isEmpty()) {
            result.put("sampleErrors", new ArrayList<>(sampleErrors));
        }
        if (!errorsByType.isEmpty()) {
            result.put("errorsByType", new LinkedHashMap<>(errorsByType));
        }
        jobStatusRegistry.recordSuccess(JOB_NAME, result);

        int attempted = digestSuccess.get() + digestFail.get();
        jobFailureNotifier.recordPartialFailure(JOB_NAME, attempted, digestFail.get());
    }

    private void processUser(User user,
                             AtomicBoolean counted,
                             AtomicInteger digestSuccess,
                             AtomicInteger digestFail,
                             AtomicInteger quizSuccess,
                             AtomicInteger quizFail,
                             List<Map<String, String>> sampleErrors,
                             ConcurrentHashMap<String, Integer> errorsByType,
                             int sampleLimit) {
        try {
            Digest digest = newsService.generateDigestForUser(user);
            // Claim this user's count now: if the 10-min timeout already claimed it,
            // the user is recorded as failed and this late completion only logs.
            boolean firstCompletion = counted.compareAndSet(false, true);
            if (!firstCompletion) {
                log.warn("Digest task for user {} completed after being counted as timed out", user.getId());
            }
            if (digest != null) {
                if (firstCompletion) {
                    digestSuccess.incrementAndGet();
                }
                try {
                    quizService.generateQuizForDigest(digest);
                    if (firstCompletion) {
                        quizSuccess.incrementAndGet();
                    }
                } catch (Exception e) {
                    if (firstCompletion) {
                        quizFail.incrementAndGet();
                    }
                    log.warn("Quiz generation failed for digest {}", digest.getId(), e);
                }
            }
            // null without exception = skipped (already exists today, or no preferences) — not a failure.
        } catch (Exception e) {
            String message = RootCauses.describe(e);
            log.error("Failed to generate digest for user {}: {}", user.getId(), message);
            if (counted.compareAndSet(false, true)) {
                digestFail.incrementAndGet();
                recordFailure(sampleErrors, errorsByType, sampleLimit, user, message);
            }
        }
    }

    private void recordFailure(List<Map<String, String>> sampleErrors,
                               ConcurrentHashMap<String, Integer> errorsByType,
                               int sampleLimit,
                               User user,
                               String message) {
        String typeKey = message.split(":", 2)[0].trim();
        errorsByType.merge(typeKey, 1, Integer::sum);
        if (sampleErrors.size() < sampleLimit) {
            Map<String, String> sample = new LinkedHashMap<>();
            sample.put("userEmail", user.getEmail());
            sample.put("message", message);
            sampleErrors.add(sample);
        }
    }
}
