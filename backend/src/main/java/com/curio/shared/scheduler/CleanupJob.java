package com.curio.shared.scheduler;

import com.curio.news.port.out.DigestPort;
import com.curio.shared.jobs.JobStatusRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CleanupJob {

    public static final String JOB_NAME = "cleanup";

    private final DigestPort digestPort;
    private final ExpiredAuthRowReaper expiredAuthRowReaper;
    private final JobStatusRegistry jobStatusRegistry;

    /**
     * Runs daily at midnight UTC to clean up data older than 30 days.
     * Deletes old digests (and cascades to quizzes and quiz_attempts) and reaps
     * expired auth rows (refresh tokens, password-reset tokens, email-verification
     * codes) so those tables do not grow without bound.
     *
     * <p>The digest cleanup and the auth-row reaping run in independent
     * transactions (the reaper is a separate bean) and separate try blocks, so a
     * failure in one still records/attempts the other.
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    @SchedulerLock(name = "cleanup", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void cleanupOldData() {
        log.info("Starting data cleanup job...");

        // UTC: generated_at is stamped in UTC, so the 30-day retention window
        // must be computed in UTC too (the ambient JVM zone is not guaranteed).
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        LocalDateTime cutoff = now.minusDays(30);

        Map<String, Object> results = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();

        try {
            long deletedDigests = digestPort.deleteByGeneratedAtBefore(cutoff);
            results.put("deletedDigests", deletedDigests);
            log.info("Removed {} digests older than {}", deletedDigests, cutoff);
        } catch (Exception e) {
            log.error("Digest cleanup failed", e);
            failures.add("digests: " + e.getMessage());
        }

        try {
            Map<String, Integer> reaped = expiredAuthRowReaper.reapExpired(now);
            results.putAll(reaped);
            log.info("Reaped expired auth rows: {}", reaped);
        } catch (Exception e) {
            log.error("Expired auth-row reaping failed", e);
            failures.add("authRows: " + e.getMessage());
        }

        results.put("cutoffDate", cutoff.toString());
        if (failures.isEmpty()) {
            jobStatusRegistry.recordSuccess(JOB_NAME, results);
        } else {
            jobStatusRegistry.recordFailure(JOB_NAME, String.join("; ", failures));
        }
    }
}
