package com.curio.admin.service;

import com.curio.admin.dto.AuditLogResponse;
import com.curio.admin.entity.AuditLog;
import com.curio.admin.port.in.AuditLogUseCase;
import com.curio.admin.port.out.AuditLogPort;
import com.curio.shared.security.UserDetailsAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditLogService implements AuditLogUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private final AuditLogPort port;

    public AuditLogService(AuditLogPort port) {
        this.port = port;
    }

    /**
     * Synchronous on purpose. The write is a single insert; @Async would lose the
     * SecurityContextHolder (and thus the actor) since Spring's default executor
     * does not propagate it. The cost is negligible vs. correctness.
     */
    @Override
    public void record(String action, String targetType, String targetId, Map<String, Object> metadata) {
        try {
            UUID actorId = null;
            String actorEmail = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserDetailsAdapter principal) {
                actorId = principal.getUser().getId();
                actorEmail = principal.getUsername();
            }

            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setMetadata(metadata);
            entry.setRequestId(MDC.get("requestId"));
            entry.setActorId(actorId);
            entry.setActorEmail(actorEmail);

            port.save(entry);
        } catch (Exception e) {
            // Audit must never break the user-facing flow.
            log.warn("audit_log_write_failed action={} target={}", action, targetId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> findRecent(int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return port.findAll(pageable).map(this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLog e) {
        return AuditLogResponse.builder()
                .id(e.getId())
                .actorId(e.getActorId())
                .actorEmail(e.getActorEmail())
                .action(e.getAction())
                .targetType(e.getTargetType())
                .targetId(e.getTargetId())
                .requestId(e.getRequestId())
                .metadata(e.getMetadata())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
