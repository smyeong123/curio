package com.curio.admin.port.in;

import com.curio.admin.dto.AuditLogResponse;
import org.springframework.data.domain.Page;

import java.util.Map;

/**
 * Inbound port for recording and reading admin audit events. Controllers depend
 * on this interface (not the concrete {@code AuditLogService}) so the hexagonal
 * ArchUnit rule stays satisfied.
 */
public interface AuditLogUseCase {
    void record(String action, String targetType, String targetId, Map<String, Object> metadata);

    /** Most-recent-first page of audit entries for the admin UI. */
    Page<AuditLogResponse> findRecent(int page, int size);
}
