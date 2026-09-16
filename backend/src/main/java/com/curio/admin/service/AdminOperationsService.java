package com.curio.admin.service;

import com.curio.admin.port.in.AdminOperationsUseCase;
import com.curio.news.entity.Digest;
import com.curio.news.port.out.DigestPort;
import com.curio.shared.scheduler.CleanupJob;
import com.curio.shared.scheduler.EmailSendJob;
import com.curio.shared.scheduler.JobStatusRegistry;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import com.curio.shared.port.in.EmailUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminOperationsService implements AdminOperationsUseCase {

    private final UserPort userPort;
    private final DigestPort digestPort;
    private final EmailUseCase emailService;
    // Manual triggers should also write to the job-status registry so the admin
    // UI shows "last ran" + per-attempt counts. Without this, the only way a
    // manual run shows up is if the scheduled run later overwrites it.
    private final JobStatusRegistry jobStatusRegistry;

    private static final int CHUNK_SIZE = 500;

    @Override
    public Map<String, Object> triggerEmailSend() {
        log.info("Admin triggered manual email send");
        int sentCount = 0;
        int failCount = 0;
        long totalProcessed = 0;

        // Page through delivery-enabled users in chunks rather than loading the
        // whole table — keeps this admin endpoint bounded as the user base grows.
        int pageIndex = 0;
        Page<User> page;
        do {
            Pageable pageable = PageRequest.of(pageIndex, CHUNK_SIZE, Sort.by("id"));
            page = userPort.findByDeliveryEnabledTrue(pageable);
            for (User user : page.getContent()) {
                try {
                    Optional<Digest> unsentDigest = digestPort
                            .findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(user.getId());

                    if (unsentDigest.isEmpty()) continue;

                    // Only count sends this run actually delivered — a lost claim means
                    // another path (hourly job / Studio) owns that digest's send.
                    if (emailService.sendDigestEmail(user, unsentDigest.get())
                            == EmailUseCase.DigestSendOutcome.SENT) {
                        sentCount++;
                    }
                } catch (Exception e) {
                    failCount++;
                    log.error("Failed to send email for user {}", user.getId(), e);
                }
            }
            totalProcessed += page.getNumberOfElements();
            pageIndex++;
        } while (page.hasNext());

        log.info("Manual email send completed: {} sent, {} failed", sentCount, failCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sentCount", sentCount);
        result.put("failCount", failCount);
        result.put("totalUsersProcessed", totalProcessed);
        jobStatusRegistry.recordSuccess(EmailSendJob.JOB_NAME, result);
        return result;
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
