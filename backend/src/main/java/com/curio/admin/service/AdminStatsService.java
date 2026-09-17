package com.curio.admin.service;

import com.curio.admin.dto.JobStatusResponse;
import com.curio.admin.dto.StatsResponse;
import com.curio.admin.dto.TopicStatusResponse;
import com.curio.admin.port.in.AdminManualJobUseCase;
import com.curio.admin.port.in.AdminStatsUseCase;
import com.curio.shared.config.TopicConstants;
import com.curio.shared.scheduler.CleanupJob;
import com.curio.shared.scheduler.DigestGenerationJob;
import com.curio.shared.scheduler.EmailSendJob;
import com.curio.shared.jobs.JobStatusRegistry;
import com.curio.news.entity.Digest;
import com.curio.news.port.out.DigestPort;
import com.curio.quiz.port.out.QuizAttemptPort;
import com.curio.user.entity.UserPreferences;
import com.curio.user.port.out.UserPort;
import com.curio.user.port.out.UserPreferencesPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminStatsService implements AdminStatsUseCase {

    private static final List<String> ALL_TOPICS = TopicConstants.ALL_TOPICS;

    /** Page size for the full digest scan in {@link #getTopicsStatus()}. */
    private static final int DIGEST_SCAN_PAGE_SIZE = 1000;

    private final UserPort userPort;
    private final UserPreferencesPort userPreferencesPort;
    private final DigestPort digestPort;
    private final QuizAttemptPort quizAttemptPort;
    private final JobStatusRegistry jobStatusRegistry;
    private final AdminManualJobUseCase adminManualJobService;

    /**
     * The two topic aggregations walk every user_preferences row and (for topic
     * status) every retained digest's JSON content — a full scan per request,
     * and the admin dashboard polls them. Results are memoized for a short TTL:
     * the underlying data only changes when digests generate or users edit
     * preferences, so a one-minute lag is invisible on an ops dashboard.
     * Replace with SQL-side aggregation (unnest/GROUP BY + jsonb) if user
     * volume outgrows this — H2's test profile is why it isn't SQL today.
     */
    private static final long STATS_CACHE_TTL_MS = 60_000;

    private record Cached<T>(T value, long atMs) {
        boolean fresh() { return System.currentTimeMillis() - atMs < STATS_CACHE_TTL_MS; }
    }

    private volatile Cached<Map<String, Long>> distributionCache;
    private volatile Cached<List<TopicStatusResponse>> topicsStatusCache;

    @Override
    @Transactional(readOnly = true)
    public StatsResponse getStats() {
        // UTC to match how Digest.generatedAt / QuizAttempt.completedAt are stamped
        // and how the rest of the codebase defines "today".
        LocalDateTime startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
        long totalUsers = userPort.count();
        long emailsSentToday = digestPort.countByEmailSentAtAfter(startOfDay);
        long quizCompletionsToday = quizAttemptPort.countByCompletedAtAfter(startOfDay);

        return StatsResponse.builder()
                .totalUsers(totalUsers)
                .emailsSentToday(emailsSentToday)
                .quizCompletionsToday(quizCompletionsToday)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getTopicDistribution() {
        Cached<Map<String, Long>> cached = distributionCache;
        if (cached != null && cached.fresh()) {
            return cached.value();
        }
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (UserPreferences preferences : userPreferencesPort.findAll()) {
            for (String topic : preferences.getTopics()) {
                distribution.merge(topic, 1L, Long::sum);
            }
        }
        Map<String, Long> result = Collections.unmodifiableMap(distribution);
        distributionCache = new Cached<>(result, System.currentTimeMillis());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<TopicStatusResponse> getTopicsStatus() {
        Cached<List<TopicStatusResponse>> cached = topicsStatusCache;
        if (cached != null && cached.fresh()) {
            return cached.value();
        }

        // UTC to match how Digest.generatedAt is stamped and how "today" is defined
        // everywhere else (V23 per-user-per-day index, the scheduled jobs).
        LocalDateTime startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay();

        // Count subscribers per topic
        Map<String, Long> subscriberCounts = new LinkedHashMap<>();
        for (String topic : ALL_TOPICS) {
            subscriberCounts.put(topic, 0L);
        }
        for (UserPreferences prefs : userPreferencesPort.findAll()) {
            for (String topic : prefs.getTopics()) {
                subscriberCounts.merge(topic, 1L, Long::sum);
            }
        }

        // Scan recent digests for topic-level stats
        Map<String, LocalDateTime> latestGeneratedAt = new HashMap<>();
        Map<String, Long> digestsTodayCount = new HashMap<>();
        for (String topic : ALL_TOPICS) {
            digestsTodayCount.put(topic, 0L);
        }

        // Page through ALL digests (ordered by generated_at DESC) to compute
        // per-topic stats; aggregates accumulate page by page so heap use stays
        // bounded to one page. Every digest must be visited — a bounded slice
        // would undercount low-frequency topics once volume passes the slice size.
        int pageIndex = 0;
        Page<Digest> digestPage;
        do {
            digestPage = digestPort.findAllByOrderByGeneratedAtDesc(
                    PageRequest.of(pageIndex, DIGEST_SCAN_PAGE_SIZE));
            for (Digest digest : digestPage.getContent()) {
                if (digest.getContent() == null) continue;
                Object generatedFor = digest.getContent().get("generatedFor");
                if (!(generatedFor instanceof List)) continue;

                List<String> topics = (List<String>) generatedFor;
                for (String topic : topics) {
                    // Track latest generated timestamp
                    if (digest.getGeneratedAt() != null) {
                        latestGeneratedAt.merge(topic, digest.getGeneratedAt(),
                                (existing, newVal) -> newVal.isAfter(existing) ? newVal : existing);
                    }
                    // Count today's digests
                    if (digest.getGeneratedAt() != null && digest.getGeneratedAt().isAfter(startOfDay)) {
                        digestsTodayCount.merge(topic, 1L, Long::sum);
                    }
                }
            }
            pageIndex++;
        } while (digestPage.hasNext());

        List<TopicStatusResponse> result = ALL_TOPICS.stream()
                .map(topic -> TopicStatusResponse.builder()
                        .topic(topic)
                        .subscriberCount(subscriberCounts.getOrDefault(topic, 0L))
                        .latestDigestGeneratedAt(latestGeneratedAt.get(topic))
                        .digestsGeneratedToday(digestsTodayCount.getOrDefault(topic, 0L))
                        .build())
                .toList();
        topicsStatusCache = new Cached<>(result, System.currentTimeMillis());
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<JobStatusResponse> getJobsStatus() {
        record JobMeta(String name, String schedule) {}
        List<JobMeta> jobs = List.of(
                new JobMeta(DigestGenerationJob.JOB_NAME, "Daily at 6:00 AM UTC"),
                new JobMeta(EmailSendJob.JOB_NAME, "Hourly at :00 UTC (per-user timezone gating)"),
                new JobMeta(CleanupJob.JOB_NAME, "Daily at midnight UTC")
        );

        return jobs.stream().map(meta -> {
            boolean running = isManualRunInFlight(meta.name());
            Map<String, Object> raw = jobStatusRegistry.getStatus(meta.name());
            if (raw == null) {
                return JobStatusResponse.builder()
                        .jobName(meta.name())
                        .schedule(meta.schedule())
                        .lastStatus("NEVER_RAN")
                        .running(running)
                        .build();
            }

            String lastRanAtStr = (String) raw.get("lastRanAt");
            java.time.LocalDateTime lastRanAt = lastRanAtStr != null
                    ? java.time.LocalDateTime.parse(lastRanAtStr)
                    : null;
            Object resultObj = raw.get("lastResult");
            Map<String, Object> lastResult = (resultObj instanceof Map) ? (Map<String, Object>) resultObj : null;

            return JobStatusResponse.builder()
                    .jobName(meta.name())
                    .schedule(meta.schedule())
                    .lastRanAt(lastRanAt)
                    .lastStatus((String) raw.getOrDefault("lastStatus", "UNKNOWN"))
                    .lastResult(lastResult)
                    .running(running)
                    .build();
        }).toList();
    }

    /**
     * Whether a manually-triggered run of the named job is in flight, per
     * {@link AdminManualJobUseCase}'s in-process flags. Only digest generation and
     * email send have a manual trigger; every other job (e.g. cleanup) is always
     * reported as not running.
     */
    private boolean isManualRunInFlight(String jobName) {
        if (DigestGenerationJob.JOB_NAME.equals(jobName)) {
            return adminManualJobService.isDigestGenerationRunning();
        }
        if (EmailSendJob.JOB_NAME.equals(jobName)) {
            return adminManualJobService.isEmailSendRunning();
        }
        return false;
    }
}
