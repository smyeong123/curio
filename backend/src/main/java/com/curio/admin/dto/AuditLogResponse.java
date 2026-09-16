package com.curio.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Read model for an audit-log entry. Keeps the JPA entity out of the response
 * body (hexagonal/ArchUnit boundary).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {
    private Long id;
    private UUID actorId;
    private String actorEmail;
    private String action;
    private String targetType;
    private String targetId;
    private String requestId;
    private Map<String, Object> metadata;
    private OffsetDateTime createdAt;
}
