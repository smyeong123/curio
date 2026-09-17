package com.curio.admin.port.in;

import com.curio.admin.dto.JobTriggerResponse;

import java.util.List;

/**
 * Inbound port for dispatching the manual admin batch jobs (digest generation,
 * email send) off the request thread.
 *
 * The underlying loops can take minutes for a non-trivial user base; running
 * them synchronously ties up a Tomcat worker and trips the nginx/browser
 * gateway timeout (504). These methods start the run in the background and
 * return immediately so the controller can answer 202 Accepted. Progress and
 * result counts are read back from the job-status registry via
 * {@code GET /api/v1/admin/jobs/status}.
 */
public interface AdminManualJobUseCase {

    /**
     * Start a digest-generation run in the background.
     *
     * @param topics optional topic filter (null/empty = all topics)
     * @return {@code started} on dispatch, or {@code already_running} if a run is
     *         already in flight.
     */
    JobTriggerResponse startDigestGeneration(List<String> topics);

    /**
     * Start an email-send run in the background.
     *
     * @return {@code started} on dispatch, or {@code already_running} if a run is
     *         already in flight.
     */
    JobTriggerResponse startEmailSend();

    /**
     * @return {@code true} while a manually-triggered digest-generation run is in
     *         flight. Reflects only manual runs (not cron-triggered ones), so a
     *         {@code false} does not guarantee no run is happening.
     */
    boolean isDigestGenerationRunning();

    /**
     * @return {@code true} while a manually-triggered email-send run is in flight.
     *         Reflects only manual runs (not cron-triggered ones).
     */
    boolean isEmailSendRunning();
}
