package com.curio.shared.digest;

import com.curio.shared.batch.SubscriberBatch;
import com.curio.shared.batch.SubscriberBatch.Outcome;
import com.curio.shared.batch.SubscriberBatch.Spec;
import com.curio.shared.batch.SubscriberBatch.Tally;
import com.curio.shared.jobs.JobRunRecorder;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPreferencesPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Generates today's digest (+ quiz) for every delivery-enabled subscriber. The
 * 06:00 UTC scheduled run and the admin "Run now" trigger are the same batch —
 * the trigger may narrow it to subscribers of given topics — so both record
 * the same result shape under {@link #JOB_NAME} for the admin dashboard, and a
 * run that dies is recorded as such by the batch itself.
 */
@Component
public class DigestBatch {

    public static final String JOB_NAME = "digest-generation";

    private final SubscriberBatch subscriberBatch;
    private final DigestPipeline pipeline;
    private final UserPreferencesPort preferencesPort;
    private final JobRunRecorder recorder;
    private final ThreadPoolTaskExecutor digestExecutor;

    public DigestBatch(SubscriberBatch subscriberBatch,
                       DigestPipeline pipeline,
                       UserPreferencesPort preferencesPort,
                       JobRunRecorder recorder,
                       @Qualifier("digestExecutor") ThreadPoolTaskExecutor digestExecutor) {
        this.subscriberBatch = subscriberBatch;
        this.pipeline = pipeline;
        this.preferencesPort = preferencesPort;
        this.recorder = recorder;
        this.digestExecutor = digestExecutor;
    }

    /** Every delivery-enabled subscriber. */
    public Map<String, Object> runForAll() {
        return run(null);
    }

    /**
     * Subscribers following at least one of {@code topicFilter} (null or empty
     * means everyone). Users outside the filter are not counted at all.
     */
    public Map<String, Object> run(List<String> topicFilter) {
        long start = System.currentTimeMillis();
        try {
            return generate(topicFilter, start);
        } catch (RuntimeException e) {
            recorder.recordFailure(JOB_NAME, e);
            throw e;
        }
    }

    private Map<String, Object> generate(List<String> topicFilter, long start) {
        Set<String> requested = (topicFilter != null && !topicFilter.isEmpty()) ? new HashSet<>(topicFilter) : null;
        Predicate<User> include = requested == null ? user -> true : user -> followsAnyOf(user, requested);

        AtomicInteger quizSuccess = new AtomicInteger();
        AtomicInteger quizFail = new AtomicInteger();

        // 10 min per user: a multi-topic cold-cache run is several sequential AI calls.
        Spec spec = Spec.forPool(JOB_NAME, digestExecutor, Duration.ofMinutes(10), 5);
        Tally tally = subscriberBatch.run(spec, null, include, user -> {
            DigestPipeline.Outcome outcome = pipeline.generateWithQuiz(user);
            return switch (outcome.status()) {
                case GENERATED -> {
                    switch (outcome.quiz()) {
                        case GENERATED -> quizSuccess.incrementAndGet();
                        case FAILED -> quizFail.incrementAndGet();
                        case NOT_ATTEMPTED -> { }
                    }
                    yield Outcome.success();
                }
                case ALREADY_EXISTS -> Outcome.skipped("already generated today");
                case NO_TOPICS -> Outcome.skipped("no topics");
                // A quiet news day, where every topic comes back empty, is a skip: it
                // must not trip the 25 % partial-failure alert. Provider outages
                // surface as thrown errors — counted failed — not as empty results.
                case NOTHING_GENERATED -> Outcome.skipped("nothing generated");
            };
        });

        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("digestSuccess", tally.success());
        extras.put("digestFail", tally.failed());
        extras.put("digestSkipped", tally.skipped());
        extras.put("quizSuccess", quizSuccess.get());
        extras.put("quizFail", quizFail.get());
        if (requested != null) {
            extras.put("topicFilter", List.copyOf(topicFilter));
        }
        return recorder.recordRun(JOB_NAME, tally, System.currentTimeMillis() - start, extras);
    }

    private boolean followsAnyOf(User user, Set<String> topics) {
        return preferencesPort.findByUserId(user.getId())
                .map(prefs -> Arrays.stream(prefs.getTopics()).anyMatch(topics::contains))
                .orElse(false);
    }
}
