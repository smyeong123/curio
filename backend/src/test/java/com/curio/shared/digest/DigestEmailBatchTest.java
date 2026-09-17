package com.curio.shared.digest;

import com.curio.news.entity.Digest;
import com.curio.news.port.out.DigestPort;
import com.curio.shared.batch.InlinePool;
import com.curio.shared.batch.SubscriberBatch;
import com.curio.shared.jobs.JobFailureNotifier;
import com.curio.shared.jobs.JobRunRecorder;
import com.curio.shared.jobs.JobStatusRegistry;
import com.curio.shared.port.in.EmailUseCase;
import com.curio.user.entity.User;
import com.curio.user.entity.UserPreferences;
import com.curio.user.port.out.UserPort;
import com.curio.user.port.out.UserPreferencesPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DigestEmailBatchTest {

    @Mock private UserPort userPort;
    @Mock private DigestPort digestPort;
    @Mock private EmailUseCase emailService;
    @Mock private DigestPipeline pipeline;
    @Mock private UserPreferencesPort preferencesPort;
    @Mock private JobStatusRegistry jobStatusRegistry;
    @Mock private JobFailureNotifier jobFailureNotifier;

    private DigestEmailBatch batch;

    /** 2026-09-16 10:00 UTC — 19:00 in Seoul. */
    private static final Instant NOW = Instant.parse("2026-09-16T10:00:00Z");

    @BeforeEach
    void setUp() {
        batch = new DigestEmailBatch(new SubscriberBatch(userPort), digestPort, emailService, pipeline,
                preferencesPort, new JobRunRecorder(jobStatusRegistry, jobFailureNotifier), InlinePool.inline(),
                Clock.fixed(NOW, ZoneOffset.UTC), "UTC");
    }

    private static User user(String email) {
        return User.builder().id(UUID.randomUUID()).email(email).build();
    }

    private static UserPreferences prefs(User user, int hour, String zone) {
        return UserPreferences.builder().user(user).topics(new String[]{"DeepSeek"}).deliveryHour(hour).timezone(zone).build();
    }

    private static Digest digestAt(User user, LocalDateTime generatedAt) {
        return Digest.builder().id(UUID.randomUUID()).user(user).generatedAt(generatedAt).build();
    }

    // --- admin "send now": every unsent digest, no hour gate ---

    @Test
    void sendAllUnsent_sendsOnlyToUsersWithAnUnsentDigest_andCountsEveryone() {
        User withDigest = user("a@example.com");
        User withoutDigest = user("b@example.com");
        Digest digest = Digest.builder().id(UUID.randomUUID()).build();
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(withDigest, withoutDigest)));
        when(digestPort.findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(withDigest.getId())).thenReturn(Optional.of(digest));
        when(digestPort.findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(withoutDigest.getId())).thenReturn(Optional.empty());
        when(emailService.sendDigestEmail(withDigest, digest)).thenReturn(EmailUseCase.DigestSendOutcome.SENT);

        Map<String, Object> result = batch.sendAllUnsent();

        verify(emailService, never()).sendDigestEmail(eq(withoutDigest), any());
        assertThat(result).containsEntry("sentCount", 1).containsEntry("failCount", 0)
                .containsEntry("usersProcessed", 2).containsEntry("usersScanned", 2L)
                .doesNotContainKey("totalUsersProcessed");
        verify(jobStatusRegistry).recordSuccess(DigestEmailBatch.JOB_NAME, result);
        verify(jobFailureNotifier).recordPartialFailure(DigestEmailBatch.JOB_NAME, 1, 0);
        verifyNoInteractions(pipeline, preferencesPort);
    }

    @Test
    void sendAllUnsent_countsFailures_withoutAborting_andAlertsOnTheRatio() {
        User u1 = user("a@example.com");
        User u2 = user("b@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(u1, u2)));
        when(digestPort.findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(any()))
                .thenReturn(Optional.of(Digest.builder().id(UUID.randomUUID()).build()));
        doThrow(new RuntimeException("resend down")).when(emailService).sendDigestEmail(eq(u1), any());
        when(emailService.sendDigestEmail(eq(u2), any())).thenReturn(EmailUseCase.DigestSendOutcome.SENT);

        Map<String, Object> result = batch.sendAllUnsent();

        assertThat(result).containsEntry("sentCount", 1).containsEntry("failCount", 1).containsEntry("usersScanned", 2L);
        verify(jobFailureNotifier).recordPartialFailure(DigestEmailBatch.JOB_NAME, 2, 1);
    }

    @Test
    void aLostSendClaim_isNeitherSentNorFailed() {
        User user = user("a@example.com");
        Digest digest = Digest.builder().id(UUID.randomUUID()).build();
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user)));
        when(digestPort.findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(user.getId())).thenReturn(Optional.of(digest));
        when(emailService.sendDigestEmail(user, digest)).thenReturn(EmailUseCase.DigestSendOutcome.ALREADY_CLAIMED);

        Map<String, Object> result = batch.sendAllUnsent();

        assertThat(result).containsEntry("sentCount", 0).containsEntry("failCount", 0);
    }

    @Test
    void aRunThatDies_isRecordedAsFailed_andRethrown() {
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(batch::sendAllUnsent).isInstanceOf(IllegalStateException.class).hasMessage("db down");

        verify(jobStatusRegistry).recordFailure(DigestEmailBatch.JOB_NAME, "IllegalStateException: db down");
        verify(jobFailureNotifier).recordFailure(eq(DigestEmailBatch.JOB_NAME), any(IllegalStateException.class));
        verify(jobStatusRegistry, never()).recordSuccess(any(), any());
    }

    // --- hourly run: delivery-hour gate, today's digest only, just-in-time generation ---

    @Test
    void sendDue_honoursEachUsersLocalDeliveryHour() {
        User due = user("due@example.com");          // 08:00 UTC target, it is 10:00 UTC → due (catch-up)
        User notYet = user("later@example.com");     // 12:00 UTC target → not yet
        User seoul = user("seoul@example.com");      // 18:00 Asia/Seoul target, it is 19:00 there → due
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(due, notYet, seoul)));
        when(preferencesPort.findByUserIdIn(any()))
                .thenReturn(List.of(prefs(due, 8, "UTC"), prefs(notYet, 12, "UTC"), prefs(seoul, 18, "Asia/Seoul")));
        LocalDateTime today = LocalDateTime.of(2026, 9, 16, 6, 0);
        when(digestPort.findFirstByUserIdOrderByGeneratedAtDesc(due.getId())).thenReturn(Optional.of(digestAt(due, today)));
        when(digestPort.findFirstByUserIdOrderByGeneratedAtDesc(seoul.getId())).thenReturn(Optional.of(digestAt(seoul, today)));
        when(emailService.sendDigestEmail(any(), any())).thenReturn(EmailUseCase.DigestSendOutcome.SENT);

        Map<String, Object> result = batch.sendDue();

        verify(emailService).sendDigestEmail(eq(due), any());
        verify(emailService).sendDigestEmail(eq(seoul), any());
        verify(emailService, never()).sendDigestEmail(eq(notYet), any());
        assertThat(result).containsEntry("sentCount", 2).containsEntry("usersProcessed", 2).containsEntry("usersScanned", 3L);
    }

    @Test
    void sendDue_generatesTodaysDigestJustInTime_whenOnlyAStaleOneIsUnsent() {
        User user = user("early@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user)));
        when(preferencesPort.findByUserIdIn(any())).thenReturn(List.of(prefs(user, 8, "UTC")));
        Digest stale = digestAt(user, LocalDateTime.of(2026, 9, 15, 6, 0));
        Digest fresh = digestAt(user, LocalDateTime.of(2026, 9, 16, 10, 0));
        when(digestPort.findFirstByUserIdOrderByGeneratedAtDesc(user.getId()))
                .thenReturn(Optional.of(stale), Optional.of(fresh));
        when(emailService.sendDigestEmail(user, fresh)).thenReturn(EmailUseCase.DigestSendOutcome.SENT);

        Map<String, Object> result = batch.sendDue();

        verify(pipeline).generateWithQuiz(user);
        verify(emailService).sendDigestEmail(user, fresh);
        verify(emailService, never()).sendDigestEmail(user, stale);
        assertThat(result).containsEntry("sentCount", 1);
    }

    @Test
    void sendDue_neverSendsAStaleDigest_whenGenerationYieldsNothing() {
        User user = user("stuck@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user)));
        when(preferencesPort.findByUserIdIn(any())).thenReturn(List.of(prefs(user, 8, "UTC")));
        Digest stale = digestAt(user, LocalDateTime.of(2026, 9, 15, 6, 0));
        when(digestPort.findFirstByUserIdOrderByGeneratedAtDesc(user.getId())).thenReturn(Optional.of(stale));

        Map<String, Object> result = batch.sendDue();

        verify(pipeline).generateWithQuiz(user);
        verifyNoInteractions(emailService);
        assertThat(result).containsEntry("sentCount", 0).containsEntry("failCount", 0);
    }

    @Test
    void sendDue_countsAJustInTimeGenerationError_asThatUsersFailure() {
        User user = user("broken@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user)));
        when(preferencesPort.findByUserIdIn(any())).thenReturn(List.of(prefs(user, 8, "UTC")));
        when(digestPort.findFirstByUserIdOrderByGeneratedAtDesc(user.getId())).thenReturn(Optional.empty());
        when(pipeline.generateWithQuiz(user)).thenThrow(new IllegalStateException("provider down"));

        Map<String, Object> result = batch.sendDue();

        verifyNoInteractions(emailService);
        assertThat(result).containsEntry("sentCount", 0).containsEntry("failCount", 1);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> samples = (List<Map<String, String>>) result.get("sampleErrors");
        assertThat(samples).singleElement().satisfies(sample -> assertThat(sample)
                .containsEntry("userEmail", "broken@example.com")
                .containsEntry("message", "generate: IllegalStateException: provider down"));
        assertThat(result.get("errorsByType")).isEqualTo(Map.of("generate", 1));
    }

    @Test
    void sendDue_stopsAfterOneQuery_whenTodaysDigestIsAlreadySent() {
        User served = user("served@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(served)));
        when(preferencesPort.findByUserIdIn(any())).thenReturn(List.of(prefs(served, 8, "UTC")));
        Digest sent = digestAt(served, LocalDateTime.of(2026, 9, 16, 6, 0));
        sent.setEmailSentAt(LocalDateTime.of(2026, 9, 16, 8, 0));
        when(digestPort.findFirstByUserIdOrderByGeneratedAtDesc(served.getId())).thenReturn(Optional.of(sent));

        Map<String, Object> result = batch.sendDue();

        verify(digestPort, times(1)).findFirstByUserIdOrderByGeneratedAtDesc(served.getId());
        verifyNoInteractions(pipeline, emailService);
        assertThat(result).containsEntry("sentCount", 0).containsEntry("failCount", 0);
    }

    @Test
    void sendDue_defaultsToEightInTheFallbackZone_whenPreferencesAreMissing() {
        User user = user("noprefs@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user)));
        when(preferencesPort.findByUserIdIn(any())).thenReturn(List.of());
        when(digestPort.findFirstByUserIdOrderByGeneratedAtDesc(user.getId()))
                .thenReturn(Optional.of(digestAt(user, LocalDateTime.of(2026, 9, 16, 6, 0))));
        when(emailService.sendDigestEmail(any(), any())).thenReturn(EmailUseCase.DigestSendOutcome.SENT);

        batch.sendDue(); // 10:00 UTC ≥ 08:00 UTC default

        verify(emailService).sendDigestEmail(eq(user), any());
    }

    @Test
    void sendDue_skipsAWholeChunk_whosePreferencesFailedToLoad_andReportsIt() {
        User user = user("a@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user)));
        when(preferencesPort.findByUserIdIn(any())).thenThrow(new IllegalStateException("prefs query failed"));

        Map<String, Object> result = batch.sendDue();

        verifyNoInteractions(digestPort, pipeline, emailService);
        assertThat(result).containsEntry("usersProcessed", 0).containsEntry("usersScanned", 1L).containsEntry("chunks", 1);
        verify(jobFailureNotifier).recordFailure(eq(DigestEmailBatch.JOB_NAME + ":chunk-0"), any(IllegalStateException.class));
        verify(jobStatusRegistry).recordSuccess(eq(DigestEmailBatch.JOB_NAME), any());
    }
}
