package com.curio.shared.jobs;

import io.sentry.Sentry;
import io.sentry.SentryLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Surfaces scheduled-job failures to operators via Sentry + structured logs.
 *
 * {@code JobRunRecorder} calls {@link #recordFailure} when a batch run dies and
 * {@link #recordPartialFailure} after every run; the latter only fires when more
 * than {@code partialFailureThreshold} items fail within a single run.
 */
@Component
public class JobFailureNotifier {

    private static final Logger log = LoggerFactory.getLogger(JobFailureNotifier.class);

    @Value("${curio.jobs.partial-failure-threshold:0.25}")
    private double partialFailureThreshold;

    public void recordFailure(String jobName, Throwable cause) {
        log.error("scheduled_job_failure job={} message={}", jobName, cause.getMessage(), cause);
        Sentry.withScope(scope -> {
            scope.setLevel(SentryLevel.ERROR);
            scope.setTag("job", jobName);
            scope.setTag("category", "scheduled_job");
            Sentry.captureException(cause);
        });
    }

    public void recordPartialFailure(String jobName, int total, int failed) {
        if (total <= 0) return;
        double ratio = (double) failed / total;
        if (ratio < partialFailureThreshold) return;

        String msg = "Job " + jobName + " partial failure: " + failed + "/" + total
                + " items failed (" + Math.round(ratio * 100) + "%)";
        log.warn("scheduled_job_partial_failure job={} failed={} total={} ratio={}",
                jobName, failed, total, ratio);
        Sentry.withScope(scope -> {
            scope.setLevel(SentryLevel.WARNING);
            scope.setTag("job", jobName);
            scope.setTag("category", "scheduled_job_partial");
            scope.setExtra("failed", String.valueOf(failed));
            scope.setExtra("total", String.valueOf(total));
            Sentry.captureMessage(msg);
        });
    }
}
