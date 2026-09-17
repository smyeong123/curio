package com.curio.studio.service;

import com.curio.news.entity.Digest;
import com.curio.news.port.out.DigestPort;
import com.curio.shared.digest.DigestPipeline;
import com.curio.shared.port.in.EmailUseCase;
import com.curio.studio.dto.StudioStatusResponse;
import com.curio.studio.dto.TaskStatus;
import com.curio.studio.service.StudioTaskStatusService.TaskType;
import com.curio.user.entity.User;
import com.curio.user.entity.UserPreferences;
import com.curio.user.port.out.UserPort;
import com.curio.user.port.out.UserPreferencesPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudioServiceTest {

    @Mock private DigestPipeline pipeline;
    @Mock private EmailUseCase emailService;
    @Mock private DigestPort digestPort;
    @Mock private UserPort userPort;
    @Mock private UserPreferencesPort userPreferencesPort;
    @Mock private StudioTaskStatusService status;
    @Mock private Executor studioExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StudioService studioService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        studioService = new StudioService(pipeline, emailService, digestPort, userPort, userPreferencesPort,
                status, objectMapper, studioExecutor);
        userId = UUID.randomUUID();
        user = User.builder().id(userId).email("reader@example.com").build();
    }

    // --- Redis map → TaskStatus ---

    @Test
    void getStatus_mapsEveryKeyTheStatusServiceWrites() {
        Map<String, Object> running = new LinkedHashMap<>();
        running.put("type", "digest");
        running.put("state", "RUNNING");
        running.put("phase", "Summarizing \"DeepSeek\"");
        running.put("current", 2);
        running.put("total", 5);
        running.put("startedAt", "2026-07-03T09:30");
        running.put("updatedAt", "2026-07-03T09:31:15.250");
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("type", "email");
        done.put("state", "SUCCESS");
        done.put("message", "Sent! Check reader@example.com.");
        done.put("startedAt", "2026-07-03T09:32");
        done.put("finishedAt", "2026-07-03T09:32:40");
        done.put("updatedAt", "2026-07-03T09:32:40");
        done.put("digestId", "0b8a4c3e-1111-2222-3333-444455556666");
        when(status.get(userId, TaskType.DIGEST)).thenReturn(running);
        when(status.get(userId, TaskType.EMAIL)).thenReturn(done);
        stubEmptyOverview();

        StudioStatusResponse response = studioService.getStatus(user);

        TaskStatus digest = response.digest();
        assertThat(digest.type()).isEqualTo("digest");
        assertThat(digest.state()).isEqualTo("RUNNING");
        assertThat(digest.phase()).isEqualTo("Summarizing \"DeepSeek\"");
        assertThat(digest.current()).isEqualTo(2);
        assertThat(digest.total()).isEqualTo(5);
        assertThat(digest.message()).isNull();
        assertThat(digest.startedAt()).isEqualTo("2026-07-03T09:30");
        assertThat(digest.finishedAt()).isNull();
        assertThat(digest.updatedAt()).isEqualTo("2026-07-03T09:31:15.250");
        assertThat(digest.digestId()).isNull();

        TaskStatus email = response.email();
        assertThat(email.state()).isEqualTo("SUCCESS");
        assertThat(email.message()).isEqualTo("Sent! Check reader@example.com.");
        assertThat(email.finishedAt()).isEqualTo("2026-07-03T09:32:40");
        assertThat(email.digestId()).isEqualTo("0b8a4c3e-1111-2222-3333-444455556666");
        assertThat(email.current()).isNull();
    }

    @Test
    void getStatus_leavesUnsetTaskFieldsOutOfTheJson() throws Exception {
        Map<String, Object> idle = new LinkedHashMap<>();
        idle.put("type", "digest");
        idle.put("state", "IDLE");
        idle.put("updatedAt", "2026-07-03T09:30");
        idle.put("phase", null);
        when(status.get(userId, TaskType.DIGEST)).thenReturn(idle);
        when(status.get(userId, TaskType.EMAIL)).thenReturn(idle);
        stubEmptyOverview();

        StudioStatusResponse response = studioService.getStatus(user);

        String json = objectMapper.writeValueAsString(response.digest());
        assertThat(json).isEqualTo("{\"type\":\"digest\",\"state\":\"IDLE\",\"updatedAt\":\"2026-07-03T09:30\"}");
    }

    @Test
    void getStatus_ignoresKeysTheTaskStatusDoesNotKnow() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("type", "email");
        record.put("state", "FAILED");
        record.put("legacyField", "whatever");
        when(status.get(userId, TaskType.DIGEST)).thenReturn(record);
        when(status.get(userId, TaskType.EMAIL)).thenReturn(record);
        stubEmptyOverview();

        assertThat(studioService.getStatus(user).email().state()).isEqualTo("FAILED");
    }

    // --- Overview ---

    @Test
    void getStatus_buildsTheOverview_fromPreferencesAndLatestDigest() {
        Map<String, Object> idle = Map.of("type", "digest", "state", "IDLE");
        when(status.get(userId, TaskType.DIGEST)).thenReturn(idle);
        when(status.get(userId, TaskType.EMAIL)).thenReturn(idle);
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.of(
                UserPreferences.builder().user(user).topics(new String[]{"DeepSeek", "Claude (Anthropic)"}).build()));
        Digest latest = Digest.builder().id(UUID.randomUUID()).user(user)
                .generatedAt(LocalDateTime.of(2026, 7, 3, 6, 0))
                .emailSentAt(null).build();
        when(digestPort.findTop5ByUserIdOrderByGeneratedAtDesc(userId)).thenReturn(List.of(latest));
        when(digestPort.findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(userId))
                .thenReturn(Optional.of(latest));

        StudioStatusResponse response = studioService.getStatus(user);

        assertThat(response.overview().email()).isEqualTo("reader@example.com");
        assertThat(response.overview().topics()).containsExactly("DeepSeek", "Claude (Anthropic)");
        assertThat(response.overview().topicCount()).isEqualTo(2);
        assertThat(response.overview().latestDigest().id()).isEqualTo(latest.getId().toString());
        assertThat(response.overview().latestDigest().generatedAt()).isEqualTo("2026-07-03T06:00");
        assertThat(response.overview().latestDigest().emailSentAt()).isNull();
        assertThat(response.overview().hasUnsentDigest()).isTrue();
    }

    @Test
    void getStatus_overviewHasNoLatestDigest_forABrandNewReader() {
        Map<String, Object> idle = Map.of("type", "digest", "state", "IDLE");
        when(status.get(userId, TaskType.DIGEST)).thenReturn(idle);
        when(status.get(userId, TaskType.EMAIL)).thenReturn(idle);
        stubEmptyOverview();

        StudioStatusResponse response = studioService.getStatus(user);

        assertThat(response.overview().topics()).isEmpty();
        assertThat(response.overview().topicCount()).isZero();
        assertThat(response.overview().latestDigest()).isNull();
        assertThat(response.overview().hasUnsentDigest()).isFalse();
    }

    // --- start* while a run is active ---

    @Test
    void startDigestGeneration_returnsTheLiveStatusWithoutStartingASecondRun_whenActive() {
        Map<String, Object> queued = Map.of("type", "digest", "state", "QUEUED", "phase", "Queued…");
        when(status.isActive(userId, TaskType.DIGEST)).thenReturn(true);
        when(status.get(userId, TaskType.DIGEST)).thenReturn(queued);

        TaskStatus result = studioService.startDigestGeneration(user);

        assertThat(result.state()).isEqualTo("QUEUED");
        assertThat(result.phase()).isEqualTo("Queued…");
        verify(status, never()).tryAcquire(userId, TaskType.DIGEST);
        verify(studioExecutor, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    private void stubEmptyOverview() {
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.empty());
        when(digestPort.findTop5ByUserIdOrderByGeneratedAtDesc(userId)).thenReturn(List.of());
        when(digestPort.findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(userId))
                .thenReturn(Optional.empty());
    }
}
