package com.curio.admin.service;

import com.curio.news.port.out.DigestPort;
import com.curio.shared.digest.DigestEmailBatch;
import com.curio.shared.scheduler.CleanupJob;
import com.curio.shared.jobs.JobStatusRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOperationsServiceTest {

    @Mock private DigestPort digestPort;
    @Mock private DigestEmailBatch emailBatch;
    @Mock private JobStatusRegistry jobStatusRegistry;

    @InjectMocks private AdminOperationsService service;

    @Test
    void triggerEmailSend_isTheBatchInSendEverythingMode() {
        Map<String, Object> outcome = Map.of("sentCount", 3);
        when(emailBatch.sendAllUnsent()).thenReturn(outcome);

        assertThat(service.triggerEmailSend()).isSameAs(outcome);
    }

    @Test
    void triggerCleanup_deletesThirtyDayOldDigests_andRecordsTheRun() {
        when(digestPort.deleteByGeneratedAtBefore(any())).thenReturn(7L);

        Map<String, Object> result = service.triggerCleanup();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(digestPort).deleteByGeneratedAtBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBefore(LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(29));
        assertThat(result).containsEntry("deletedDigests", 7L).containsKey("cutoffDate");
        verify(jobStatusRegistry).recordSuccess(eq(CleanupJob.JOB_NAME), any());
    }
}
