package com.curio.admin.controller;

import com.curio.admin.dto.AdminDigestResponse;
import com.curio.admin.dto.AdminUserDetail;
import com.curio.admin.dto.AdminUserSummary;
import com.curio.admin.dto.AuditLogResponse;
import com.curio.admin.dto.GenerateDigestsRequest;
import com.curio.admin.dto.JobStatusResponse;
import com.curio.admin.dto.JobTriggerResponse;
import com.curio.admin.dto.StatsResponse;
import com.curio.admin.dto.TopicStatusResponse;
import com.curio.admin.port.in.AdminDigestUseCase;
import com.curio.admin.port.in.AdminManualJobUseCase;
import com.curio.admin.port.in.AdminOperationsUseCase;
import com.curio.admin.port.in.AdminStatsUseCase;
import com.curio.admin.port.in.AdminUserUseCase;
import com.curio.admin.port.in.AuditLogUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin endpoints use three authority tiers:
 *   READ  ({@code ADMIN_READ})  — list/detail/stats queries; safe for auditors
 *   WRITE ({@code ADMIN_WRITE}) — trigger jobs (digest generation, email send)
 *   SUPER ({@code ADMIN_SUPER}) — destructive/maintenance ops (cleanup)
 * Holding the legacy {@code ROLE_ADMIN} satisfies any tier for backwards compat.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin management and analytics endpoints")
public class AdminController {

    private static final String READ = "hasRole('ADMIN') or hasAuthority('ADMIN_READ') or hasAuthority('ADMIN_WRITE') or hasAuthority('ADMIN_SUPER')";
    private static final String WRITE = "hasRole('ADMIN') or hasAuthority('ADMIN_WRITE') or hasAuthority('ADMIN_SUPER')";
    private static final String SUPER = "hasRole('ADMIN') or hasAuthority('ADMIN_SUPER')";

    private final AdminUserUseCase adminUserService;
    private final AdminDigestUseCase adminDigestService;
    private final AdminStatsUseCase adminStatsService;
    private final AdminOperationsUseCase adminOperationsService;
    private final AdminManualJobUseCase adminManualJobService;
    private final AuditLogUseCase auditLogService;

    @GetMapping("/users")
    @PreAuthorize(READ)
    @Operation(summary = "List users with optional email search")
    public ResponseEntity<Page<AdminUserSummary>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search) {
        size = Math.min(size, 100);
        return ResponseEntity.ok(adminUserService.getUsers(page, size, search));
    }

    @GetMapping("/users/{id}")
    @PreAuthorize(READ)
    @Operation(summary = "Get a user detail payload")
    public ResponseEntity<AdminUserDetail> getUserDetail(@PathVariable String id) {
        return ResponseEntity.ok(adminUserService.getUserDetail(UUID.fromString(id)));
    }

    @GetMapping("/stats")
    @PreAuthorize(READ)
    @Operation(summary = "Get platform statistics summary")
    public ResponseEntity<StatsResponse> getStats() {
        return ResponseEntity.ok(adminStatsService.getStats());
    }

    @PostMapping("/generate-digests")
    @PreAuthorize(WRITE)
    @Operation(summary = "Start digest generation in the background (optionally filtered by topics); poll /admin/jobs/status for results")
    public ResponseEntity<JobTriggerResponse> triggerDigestGeneration(
            @RequestBody(required = false) GenerateDigestsRequest request) {
        List<String> topics = (request != null) ? request.getTopics() : null;
        // Dispatch to a background executor and return promptly — the run can take
        // minutes and would otherwise trip the reverse-proxy gateway timeout.
        JobTriggerResponse outcome = adminManualJobService.startDigestGeneration(topics);
        // Audit AFTER the start attempt so a no-op ("already_running") is recorded
        // as such rather than looking like a second real run.
        auditLogService.record("admin.generate_digests", "job", "digest-generation",
                Map.of("topicsFilter", topics == null ? List.of() : topics,
                        "status", outcome.status()));
        return ResponseEntity.accepted().body(outcome);
    }

    @GetMapping("/stats/topics")
    @PreAuthorize(READ)
    @Operation(summary = "Get topic preference distribution")
    public ResponseEntity<Map<String, Long>> getTopicDistribution() {
        return ResponseEntity.ok(adminStatsService.getTopicDistribution());
    }

    @PostMapping("/send-emails")
    @PreAuthorize(WRITE)
    @Operation(summary = "Start email send batch in the background; poll /admin/jobs/status for results")
    public ResponseEntity<JobTriggerResponse> triggerEmailSend() {
        // Dispatch to a background executor and return promptly — the batch can take
        // minutes and would otherwise trip the reverse-proxy gateway timeout.
        JobTriggerResponse outcome = adminManualJobService.startEmailSend();
        auditLogService.record("admin.send_emails", "job", "email-send",
                Map.of("status", outcome.status()));
        return ResponseEntity.accepted().body(outcome);
    }

    @PostMapping("/cleanup")
    @PreAuthorize(SUPER)
    @Operation(summary = "Manually trigger 30-day data cleanup")
    public ResponseEntity<Map<String, Object>> triggerCleanup() {
        auditLogService.record("admin.cleanup", "job", "cleanup", Map.of());
        return ResponseEntity.ok(adminOperationsService.triggerCleanup());
    }

    @GetMapping("/digests")
    @PreAuthorize(READ)
    @Operation(summary = "Paginated list of all users' digests with optional filters")
    public ResponseEntity<Page<AdminDigestResponse>> getDigests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String userEmail) {
        size = Math.min(size, 100);
        return ResponseEntity.ok(adminDigestService.getDigests(page, size, topic, userEmail));
    }

    @GetMapping("/topics/status")
    @PreAuthorize(READ)
    @Operation(summary = "Get all topics with subscriber count, latest digest timestamp, and today's digest count")
    public ResponseEntity<List<TopicStatusResponse>> getTopicsStatus() {
        return ResponseEntity.ok(adminStatsService.getTopicsStatus());
    }

    @GetMapping("/jobs/status")
    @PreAuthorize(READ)
    @Operation(summary = "Get the last run status and result for each scheduled job")
    public ResponseEntity<List<JobStatusResponse>> getJobsStatus() {
        return ResponseEntity.ok(adminStatsService.getJobsStatus());
    }

    @GetMapping("/audit-log")
    @PreAuthorize(READ)
    @Operation(summary = "Paginated, most-recent-first list of admin audit-log entries")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auditLogService.findRecent(page, size));
    }
}
