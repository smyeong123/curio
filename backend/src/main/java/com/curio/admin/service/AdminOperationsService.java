package com.curio.admin.service;

import com.curio.admin.port.in.AdminOperationsUseCase;
import com.curio.news.port.out.DigestPort;
import com.curio.shared.digest.DigestEmailBatch;
import com.curio.shared.scheduler.CleanupJob;
import com.curio.shared.jobs.JobStatusRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminOperationsService implements AdminOperationsUseCase {

    private final DigestPort digestPort;
    /** The same batch the hourly job runs, in its "send everything unsent now" mode. */
    private final DigestEmailBatch emailBatch;
    // Manual triggers also write to the job-status registry so the admin UI shows
    // "last ran" + per-attempt counts for them, not only for cron runs.
    private final JobStatusRegistry jobStatusRegistry;

    @Override
    public Map<String, Object> triggerEmailSend() {
        log.info("Admin triggered manual email send");
        return emailBatch.sendAllUnsent();
    }

    // Deliberately NOT @Transactional: the adapter deletes in independent batches
    // so no single transaction holds locks across the whole retention sweep.
    @Override
    public Map<String, Object> triggerCleanup() {
        log.info("Admin triggered manual cleanup");
        // UTC to match how generated_at is stamped.
        LocalDateTime cutoff = LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(30);

        long deletedDigests = digestPort.deleteByGeneratedAtBefore(cutoff);

        log.info("Manual cleanup complete: removed {} digests older than {}", deletedDigests, cutoff);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deletedDigests", deletedDigests);
        // Use ISO string — the Redis serializer (GenericJackson2JsonRedisSerializer)
        // can't round-trip LocalDateTime as Object, and putting raw temporal types
        // here makes the recordSuccess() call silently fail with a Jackson type-id error.
        result.put("cutoffDate", cutoff.toString());
        jobStatusRegistry.recordSuccess(CleanupJob.JOB_NAME, result);
        return result;
    }
}
