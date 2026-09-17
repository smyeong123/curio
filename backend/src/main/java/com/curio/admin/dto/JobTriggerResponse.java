package com.curio.admin.dto;

/**
 * 202 body of the fire-and-forget batch triggers ({@code POST /admin/generate-digests},
 * {@code POST /admin/send-emails}). Progress and results are read back from
 * {@code GET /admin/jobs/status}.
 *
 * @param status {@code started} when a run was dispatched, {@code already_running}
 *               when one was in flight and this call was a no-op
 */
public record JobTriggerResponse(String status) {

    public static final String STARTED = "started";
    public static final String ALREADY_RUNNING = "already_running";

    public static JobTriggerResponse started() {
        return new JobTriggerResponse(STARTED);
    }

    public static JobTriggerResponse alreadyRunning() {
        return new JobTriggerResponse(ALREADY_RUNNING);
    }
}
