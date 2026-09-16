package com.curio.admin.port.out;

import com.curio.admin.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Outbound port for audit-log persistence. Services depend on this interface;
 * the JPA adapter is the infrastructure implementation.
 */
public interface AuditLogPort {
    AuditLog save(AuditLog entry);

    Page<AuditLog> findAll(Pageable pageable);
}
