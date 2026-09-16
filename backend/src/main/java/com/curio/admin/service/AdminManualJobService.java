package com.curio.admin.service;

import com.curio.admin.port.in.AdminDigestUseCase;
import com.curio.admin.port.in.AdminManualJobUseCase;
import com.curio.admin.port.in.AdminOperationsUseCase;
import com.curio.shared.scheduler.DigestGenerationJob;
import com.curio.shared.scheduler.EmailSendJob;
import com.curio.shared.scheduler.JobStatusRegistry;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the manual admin batch jobs (digest generation, email send) off the
 * request thread.
 *
 * <p>The underlying loops ({@link AdminDigestUseCase#triggerDigestGeneration}
 * and {@link AdminOperationsUseCase#triggerEmailSend}) can take minutes for a
 * non-trivial user base. Running them synchronously ties up a Tomcat worker and
 * trips the nginx/browser gateway timeout (504), after which the job keeps
 * running server-side and an impatient admin may re-trigger — doubling the load
 * on the AI provider.
 *
 * <p>Each trigger is dispatched to a small dedicated pool, kept separate from
 * {@code digestExecutor}/{@code emailExecutor} whose {@code CallerRunsPolicy}
 * would run the work on the request thread when saturated. An
 * {@link AtomicBoolean} per job type makes a re-trigger a no-op while a run is
 * in flight (mirroring the Studio "already running" behavior), closing the
 * double-AI-load hole. The underlying methods still write their result counts
 * to the {@code JobStatusRegistry}, so the UI reads them back from
 * {@code GET /api/v1/admin/jobs/status}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminManualJobService implements AdminManualJobUseCase {

    private final AdminDigestUseCase adminDigestService;
    private final AdminOperationsUseCase adminOperationsService;
    private final JobStatusRegistry jobStatusRegistry;

    // Two threads so a digest run and an email run can proceed independently
    // (the per-type AtomicBoolean still prevents duplicate runs of the same job).
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "admin-manual-job");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean digestRunning = new AtomicBoolean(false);
    private final AtomicBoolean emailRunning = new AtomicBoolean(false);

    @Override
    public Map<String, Object> startDigestGeneration(List<String> topics) {
        if (!digestRunning.compareAndSet(false, true)) {
            return Map.<String, Object>of("status", "already_running");
        }
        executor.submit(() -> {
            try {
                if (topics != null && !topics.isEmpty()) {
                    adminDigestService.triggerDigestGeneration(topics);
                } else {
                    adminDigestService.triggerDigestGeneration();
                }
            } catch (Exception e) {
                log.error("Manual digest generation run failed", e);
                // Without this, /jobs/status keeps showing the PREVIOUS run's
                // success — the admin would never see that this run died.
                jobStatusRegistry.recordFailure(DigestGenerationJob.JOB_NAME, e.getMessage());
            } finally {
                digestRunning.set(false);
            }
        });
        return Map.<String, Object>of("status", "started");
    }

    @Override
    public Map<String, Object> startEmailSend() {
        if (!emailRunning.compareAndSet(false, true)) {
            return Map.<String, Object>of("status", "already_running");
        }
        executor.submit(() -> {
            try {
                adminOperationsService.triggerEmailSend();
            } catch (Exception e) {
                log.error("Manual email send run failed", e);
                jobStatusRegistry.recordFailure(EmailSendJob.JOB_NAME, e.getMessage());
            } finally {
                emailRunning.set(false);
            }
        });
        return Map.<String, Object>of("status", "started");
    }

    @Override
    public boolean isDigestGenerationRunning() {
        return digestRunning.get();
    }

    @Override
    public boolean isEmailSendRunning() {
        return emailRunning.get();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
