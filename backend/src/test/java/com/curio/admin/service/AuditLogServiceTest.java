package com.curio.admin.service;

import com.curio.admin.dto.AuditLogResponse;
import com.curio.admin.entity.AuditLog;
import com.curio.admin.port.out.AuditLogPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock private AuditLogPort port;
    @InjectMocks private AuditLogService service;

    @Test
    void findRecent_mapsEntityToResponse_sortedDescAndSizeCapped() {
        AuditLog entry = new AuditLog();
        entry.setId(7L);
        entry.setAction("admin.generate_digests");
        entry.setTargetType("job");
        entry.setTargetId("digest-generation");
        entry.setActorEmail("admin@example.com");
        entry.setMetadata(Map.of("topicsFilter", List.of()));
        entry.setCreatedAt(OffsetDateTime.now());

        when(port.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entry)));

        Page<AuditLogResponse> result = service.findRecent(0, 5000);

        assertThat(result.getContent()).hasSize(1);
        AuditLogResponse r = result.getContent().get(0);
        assertThat(r.getId()).isEqualTo(7L);
        assertThat(r.getAction()).isEqualTo("admin.generate_digests");
        assertThat(r.getActorEmail()).isEqualTo("admin@example.com");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(port).findAll(captor.capture());
        Pageable used = captor.getValue();
        assertThat(used.getPageSize()).isEqualTo(100); // capped from 5000
        assertThat(used.getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }
}
