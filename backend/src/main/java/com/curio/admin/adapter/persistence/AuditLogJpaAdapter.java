package com.curio.admin.adapter.persistence;

import com.curio.admin.entity.AuditLog;
import com.curio.admin.port.out.AuditLogPort;
import com.curio.admin.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditLogJpaAdapter implements AuditLogPort {

    private final AuditLogRepository repository;

    @Override
    public AuditLog save(AuditLog entry) {
        return repository.save(entry);
    }

    @Override
    public Page<AuditLog> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }
}
