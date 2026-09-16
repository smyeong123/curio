package com.curio.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class JobStatusResponse {

    private String jobName;
    private String schedule;
    private LocalDateTime lastRanAt;
    private String lastStatus; // SUCCESS, FAILED, NEVER_RAN
    private Map<String, Object> lastResult;
    /**
     * Whether a manually-triggered run of this job is in flight right now. Backed
     * by {@code AdminManualJobService}'s in-process flags, so it only reflects
     * runs started via the admin endpoints (not cron-triggered runs); jobs
     * without a manual trigger (e.g. cleanup) are always {@code false}.
     */
    private boolean running;
}
