package com.curio.shared.jobs;

import com.curio.shared.batch.SubscriberBatch;
import com.curio.shared.batch.SubscriberBatch.Outcome;
import com.curio.shared.batch.SubscriberBatch.Spec;
import com.curio.shared.batch.SubscriberBatch.Tally;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobRunRecorderTest {

    @Mock private UserPort userPort;
    @Mock private JobStatusRegistry jobStatusRegistry;
    @Mock private JobFailureNotifier jobFailureNotifier;

    @InjectMocks private JobRunRecorder recorder;

    private static User user(String email) {
        return User.builder().id(UUID.randomUUID()).email(email).build();
    }

    /** Two chunks: the first one's preload throws, the second has one success and one failure. */
    private Tally aRunWithAChunkFailureAndAUserFailure() {
        User a = user("a@example.com");
        User ok = user("ok@example.com");
        User bounced = user("bounced@example.com");
        int size = SubscriberBatch.CHUNK_SIZE;
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a), PageRequest.of(0, size), size + 2))
                .thenReturn(new PageImpl<>(List.of(ok, bounced), PageRequest.of(1, size), size + 2));
        AtomicInteger preloads = new AtomicInteger();
        Spec spec = new Spec("job-x", Runnable::run, Duration.ofSeconds(1), 4, 5);
        return new SubscriberBatch(userPort).run(spec,
                chunk -> { if (preloads.getAndIncrement() == 0) throw new IllegalStateException("prefs down"); },
                u -> true,
                u -> u == bounced ? Outcome.failed("SendException: bounced") : Outcome.success());
    }

    @Test
    void recordRun_recordsCommonFieldsPlusExtras_andRaisesChunkAndPartialFailures() {
        Tally tally = aRunWithAChunkFailureAndAUserFailure();

        Map<String, Object> result = recorder.recordRun("job-x", tally, 77L, Map.of("sentCount", 1));

        assertThat(result).containsEntry("durationMs", 77L).containsEntry("usersProcessed", 2)
                .containsEntry("usersScanned", 3L).containsEntry("chunks", 2)
                .containsEntry("chunkSize", SubscriberBatch.CHUNK_SIZE).containsEntry("sentCount", 1)
                .containsEntry("errorsByType", Map.of("SendException", 1));
        assertThat(result.get("sampleErrors")).isEqualTo(List.of(
                Map.of("userEmail", "bounced@example.com", "message", "SendException: bounced")));
        verify(jobStatusRegistry).recordSuccess("job-x", result);
        verify(jobFailureNotifier).recordFailure(eq("job-x:chunk-0"), any(IllegalStateException.class));
        verify(jobFailureNotifier).recordPartialFailure("job-x", 2, 1);
    }

    @Test
    void recordFailure_recordsTheRootCauseForTheDashboard_andAlertsWithTheException() {
        Exception dead = new IllegalStateException("query failed", new SQLException("connection refused"));

        recorder.recordFailure("job-x", dead);

        verify(jobStatusRegistry).recordFailure("job-x", "SQLException: connection refused");
        verify(jobFailureNotifier).recordFailure("job-x", dead);
    }
}
