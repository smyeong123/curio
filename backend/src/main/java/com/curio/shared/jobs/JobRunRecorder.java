package com.curio.shared.jobs;

import com.curio.shared.batch.SubscriberBatch.Tally;
import com.curio.shared.exception.RootCauses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The one way a subscriber batch reports how it ended, so the digest and email
 * jobs keep identical bookkeeping: the admin dashboard's result record, the
 * operator alerts (chunk failures, partial-failure ratio, dead runs) and the
 * completion log line all come from here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobRunRecorder {

    private final JobStatusRegistry jobStatusRegistry;
    private final JobFailureNotifier jobFailureNotifier;

    /**
     * Records a run that got to the end: the tally's common fields plus the
     * batch's own counters become the dashboard record, and every chunk that
     * failed to load or a failure ratio over the threshold is raised to operators.
     *
     * @return the recorded result map, for callers that return it to an admin
     */
    public Map<String, Object> recordRun(String jobName, Tally tally, long durationMs, Map<String, Object> extraFields) {
        Map<String, Object> result = tally.commonFields(durationMs);
        result.putAll(extraFields);
        jobStatusRegistry.recordSuccess(jobName, result);

        for (Tally.ChunkFailure failure : tally.chunkFailures()) {
            jobFailureNotifier.recordFailure(jobName + ":chunk-" + failure.chunkIndex(), failure.cause());
        }
        jobFailureNotifier.recordPartialFailure(jobName, tally.success() + tally.failed(), tally.failed());

        log.info("{} completed in {}ms: {} users scanned, {} attempted in {} chunks — {} ok, {} failed, {} skipped; {}",
                jobName, durationMs, tally.scanned(), tally.included(), tally.chunks(),
                tally.success(), tally.failed(), tally.skipped(), extraFields);
        return result;
    }

    /** Records a run that died before it could tally, so the dashboard shows this run, not the last good one. */
    public void recordFailure(String jobName, Exception e) {
        jobStatusRegistry.recordFailure(jobName, RootCauses.describe(e));
        jobFailureNotifier.recordFailure(jobName, e);
    }
}
