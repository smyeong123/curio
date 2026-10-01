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

    /**
     * The 05:00 KST run that opens the 2026-09-16 digest day, stamped in UTC
     * (05:00 KST = 20:00 UTC the day before).
     */
    private static final LocalDateTime RUN_0916 = LocalDateTime.of(2026, 9, 15, 20, 0);

    @BeforeEach
    void setUp() {
        batch = batchAt(NOW, "UTC");
    }

    private DigestEmailBatch batchAt(Instant now, String fallbackZone) {
        return new DigestEmailBatch(new SubscriberBatch(userPort), digestPort, emailService, pipeline,
                preferencesPort, new JobRunRecorder(jobStatusRegistry, jobFailureNotifier), InlinePool.inline(),
                Clock.fixed(now, ZoneOffset.UTC), fallbackZone);
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

    // --- hourly run: delivery-hour gate, the due digest day only, just-in-time fallback ---

    @Test
    void sendDue_honoursEachUsersLocalDeliveryHour() {
        User due = user("due@example.com");          // 08:00 UTC target, it is 10:00 UTC → due (catch-up)
        User notYet = user("later@example.com");     // 12:00 UTC target → not yet
        User seoul = user("seoul@example.com");      // 18:00 Asia/Seoul target, it is 19:00 there → due
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(due, notYet, seoul)));
        when(preferencesPort.findByUserIdIn(any()))
                .thenReturn(List.of(prefs(due, 8, "UTC"), prefs(notYet, 12, "UTC"), prefs(seoul, 18, "Asia/Seoul")));
        when(digestPort.findFirstByUserIdOrderByGeneratedAtDesc(due.getId())).thenReturn(Optional.of(digestAt(due, RUN_0916)));
        when(digestPort.findFirstByUserIdOrderByGeneratedAtDesc(seoul.getId())).thenReturn(Optional.of(digestAt(seoul, RUN_0916)));
        when(emailService.sendDigestEmail(any(), any())).thenReturn(EmailUseCase.DigestSendOutcome.SENT);

        Map<String, Object> result = batch.sendDue();

        verify(emailService).sendDigestEmail(eq(due), any());
        verify(emailService).sendDigestEmail(eq(seoul), any());
        verify(emailService, never()).sendDigestEmail(eq(notYet), any());
        assertThat(result).containsEntry("sentCount", 2).containsEntry("usersProcessed", 2).containsEntry("usersScanned", 3L);
    }

    @Test
    void sendDue_generatesTheDueDigestJustInTime_whenOnlyAStaleOneIsUnsent() {
        User user = user("early@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user)));
        when(preferencesPort.findByUserIdIn(any())).thenReturn(List.of(prefs(user, 8, "UTC")));
        Digest stale = digestAt(user, LocalDateTime.of(2026, 9, 14, 20, 0)); // the 09-15 digest day
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
        Digest stale = digestAt(user, LocalDateTime.of(2026, 9, 14, 20, 0)); // the 09-15 digest day
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
    void sendDue_stopsAfterOneQuery_whenTheDueDigestIsAlreadySent() {
        User served = user("served@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(served)));
        when(preferencesPort.findByUserIdIn(any())).thenReturn(List.of(prefs(served, 8, "UTC")));
        Digest sent = digestAt(served, RUN_0916);
        sent.setEmailSentAt(LocalDateTime.of(2026, 9, 16, 8, 0));
        when(digestPort.findFirstByUserIdOrderByGeneratedAtDesc(served.getId())).thenReturn(Optional.of(sent));

        Map<String, Object> result = batch.sendDue();

        verify(digestPort, times(1)).findFirstByUserIdOrderByGeneratedAtDesc(served.getId());
        verifyNoInteractions(pipeline, emailService);
        assertThat(result).containsEntry("sentCount", 0).containsEntry("failCount", 0);
    }

    @Test
    void sendDue_defaultsToSixInTheFallbackZone_whenPreferencesAreMissing() {
        // 21:30 UTC = 06:30 in Seoul on 09-17: due under the 06:00 default, and the
        // digest due is that morning's 05:00 KST run.
        DigestEmailBatch seoulFallback = batchAt(Instant.parse("2026-09-16T21:30:00Z"), "Asia/Seoul");
        User user = user("noprefs@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user)));
        when(preferencesPort.findByUserIdIn(any())).thenReturn(List.of());
        Digest run0917 = digestAt(user, LocalDateTime.of(2026, 9, 16, 20, 0));
        when(digestPort.findFirstByUserIdOrderByGeneratedAtDesc(user.getId())).thenReturn(Optional.of(run0917));
        when(emailService.sendDigestEmail(user, run0917)).thenReturn(EmailUseCase.DigestSendOutcome.SENT);

        seoulFallback.sendDue();

        verify(emailService).sendDigestEmail(user, run0917);
        verifyNoInteractions(pipeline);
    }

    @Test
    void sendDue_isNotYetDue_beforeSixInTheFallbackZone() {
        // 20:30 UTC = 05:30 in Seoul: the 05:00 run exists but the reader's 06:00 hasn't come.
        DigestEmailBatch seoulFallback = batchAt(Instant.parse("2026-09-16T20:30:00Z"), "Asia/Seoul");
        User user = user("noprefs@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user)));
        when(preferencesPort.findByUserIdIn(any())).thenReturn(List.of());

        seoulFallback.sendDue();

        verifyNoInteractions(digestPort, pipeline, emailService);
    }

    // --- one 05:00 KST run, each reader at their own local hour ---

    @Test
    void seoulReaderAtSix_getsThatMorningsRun_anHourAfterItIsGenerated() {
        DigestEmailBatch at = batchAt(Instant.parse("2026-09-16T21:00:00Z"), "UTC"); // 06:00 KST 09-17
        User reader = user("seoul@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(reader)));
        when(preferencesPort.findByUserIdIn(any())).thenReturn(List.of(prefs(reader, 6, "Asia/Seoul")));
        Digest run0917 = digestAt(reader, LocalDateTime.of(2026, 9, 16, 20, 0));
        when(digestPort.findFirstByUserIdOrderByGeneratedAtDesc(reader.getId())).thenReturn(Optional.of(run0917));
        when(emailService.sendDigestEmail(reader, run0917)).thenReturn(EmailUseCase.DigestSendOutcome.SENT);

        at.sendDue();

        verify(emailService).sendDigestEmail(reader, run0917);
        verifyNoInteractions(pipeline);
    }

    @Test
    void seoulReader_isNotRegeneratedOrResent_atUtcMidnight() {
        // Regression: the UTC-day gate treated 00:00 UTC (09:00 KST) as a new day, generated
        // another digest just-in-time and mailed it — so 06:00 readers got mail at 09:00.
        DigestEmailBatch at = batchAt(Instant.parse("2026-09-17T00:00:00Z"), "UTC"); // 09:00 KST 09-17
        User reader = user("seoul@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(reader)));
        when(preferencesPort.findByUserIdIn(any())).thenReturn(List.of(prefs(reader, 6, "Asia/Seoul")));
        Digest run0917 = digestAt(reader, LocalDateTime.of(2026, 9, 16, 20, 0));
        run0917.setEmailSentAt(LocalDateTime.of(2026, 9, 16, 21, 0));
        when(digestPort.findFirstByUserIdOrderByGeneratedAtDesc(reader.getId())).thenReturn(Optional.of(run0917));

        at.sendDue();

        verifyNoInteractions(pipeline, emailService);
    }

    @Test
    void newYorkReaderAtSix_getsTheRunGeneratedTheEveningBefore() {
        DigestEmailBatch at = batchAt(Instant.parse("2026-09-17T10:00:00Z"), "UTC"); // 06:00 EDT 09-17
        User reader = user("nyc@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(reader)));
        when(preferencesPort.findByUserIdIn(any())).thenReturn(List.of(prefs(reader, 6, "America/New_York")));
        Digest run0917 = digestAt(reader, LocalDateTime.of(2026, 9, 16, 20, 0)); // 16:00 EDT 09-16
        when(digestPort.findFirstByUserIdOrderByGeneratedAtDesc(reader.getId())).thenReturn(Optional.of(run0917));
        when(emailService.sendDigestEmail(reader, run0917)).thenReturn(EmailUseCase.DigestSendOutcome.SENT);

        at.sendDue();

        verify(emailService).sendDigestEmail(reader, run0917);
        verifyNoInteractions(pipeline);
    }

    @Test
    void newYorkReader_waitsUntilTomorrow_forARunThatLandsInTheirAfternoon() {
        // Regression: the catch-up gate (hour >= 6) is open all afternoon, so the 05:00 KST
        // run landing at 16:00 EDT used to go out immediately — a second email that day.
        DigestEmailBatch at = batchAt(Instant.parse("2026-09-17T20:00:00Z"), "UTC"); // 16:00 EDT 09-17
        User reader = user("nyc@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(reader)));
        when(preferencesPort.findByUserIdIn(any())).thenReturn(List.of(prefs(reader, 6, "America/New_York")));
        Digest run0918 = digestAt(reader, LocalDateTime.of(2026, 9, 17, 20, 0));
        when(digestPort.findFirstByUserIdOrderByGeneratedAtDesc(reader.getId())).thenReturn(Optional.of(run0918));

        at.sendDue();

        verifyNoInteractions(pipeline, emailService);
    }

    @Test
    void lateSignup_inTheirEvening_getsTheJustInTimeDigestAtTomorrowsHour_notTonight() {
        // Signed up after that morning's run: the fallback generates now (a newer digest day),
        // which is not today's due day, so it waits for tomorrow's 06:00.
        DigestEmailBatch at = batchAt(Instant.parse("2026-09-17T21:00:00Z"), "UTC"); // 17:00 EDT 09-17
        User reader = user("new@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(reader)));
        when(preferencesPort.findByUserIdIn(any())).thenReturn(List.of(prefs(reader, 6, "America/New_York")));
        Digest jit = digestAt(reader, LocalDateTime.of(2026, 9, 17, 21, 0));
        when(digestPort.findFirstByUserIdOrderByGeneratedAtDesc(reader.getId()))
                .thenReturn(Optional.empty(), Optional.of(jit));

        at.sendDue();

        verify(pipeline).generateWithQuiz(reader);
        verifyNoInteractions(emailService);
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
