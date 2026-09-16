package com.curio.news.service;

import com.curio.news.dto.DigestResponse;
import com.curio.news.dto.NewsSummary;
import com.curio.news.entity.Digest;
import com.curio.news.port.out.DigestPort;
import com.curio.shared.exception.ResourceNotFoundException;
import com.curio.user.entity.User;
import com.curio.user.entity.UserPreferences;
import com.curio.user.port.out.UserPort;
import com.curio.user.port.out.UserPreferencesPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

    @Mock private DigestPort digestPort;
    @Mock private UserPort userPort;
    @Mock private UserPreferencesPort userPreferencesPort;
    @Mock private AiService aiService;
    @Mock private com.curio.user.port.in.UserApiKeyUseCase userApiKeyService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private NewsService newsService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        newsService = new NewsService(digestPort, userPort,
                userPreferencesPort, aiService, objectMapper, userApiKeyService);
        // BYOK lookup defaults to "no key" — tests that exercise BYOK can override.
        org.springframework.test.util.ReflectionTestUtils.setField(newsService, "platformProvider", "claude");
        lenient().when(userApiKeyService.resolveProviderForUser(any(), any()))
                .thenReturn(java.util.Optional.empty());
        lenient().when(userApiKeyService.getDecryptedKey(any(), any()))
                .thenReturn(java.util.Optional.empty());
        userId = UUID.randomUUID();
        testUser = User.builder().id(userId).email("test@example.com").build();
    }

    // --- getDigests ---

    @Test
    void getDigests_returnsFullArchive() {
        Digest digest = Digest.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .content(Map.of("summaries", List.of()))
                .generatedAt(LocalDateTime.now())
                .build();
        when(digestPort.findByUserIdOrderByGeneratedAtDesc(eq(userId), any()))
                .thenReturn(new PageImpl<>(List.of(digest)));

        Page<DigestResponse> result = newsService.getDigests(userId, 0, 10);

        assertThat(result).hasSize(1);
        verify(digestPort).findByUserIdOrderByGeneratedAtDesc(eq(userId), any(PageRequest.class));
        verify(digestPort, never()).findByUserIdAndGeneratedAtAfterOrderByGeneratedAtDesc(any(), any(), any());
    }

    // --- getDigest ---

    @Test
    void getDigest_returnsDigest_whenOwned() {
        UUID digestId = UUID.randomUUID();
        Digest digest = Digest.builder()
                .id(digestId)
                .user(testUser)
                .content(Map.of("summaries", List.of()))
                .generatedAt(LocalDateTime.now())
                .build();
        when(digestPort.findById(digestId)).thenReturn(Optional.of(digest));

        DigestResponse result = newsService.getDigest(digestId, userId);

        assertThat(result.getId()).isEqualTo(digestId);
    }

    @Test
    void getDigest_throwsNotFound_whenNotOwned() {
        UUID digestId = UUID.randomUUID();
        User otherUser = User.builder().id(UUID.randomUUID()).build();
        Digest digest = Digest.builder()
                .id(digestId)
                .user(otherUser)
                .content(Map.of())
                .build();
        when(digestPort.findById(digestId)).thenReturn(Optional.of(digest));

        assertThatThrownBy(() -> newsService.getDigest(digestId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getDigest_throwsNotFound_whenDigestMissing() {
        UUID digestId = UUID.randomUUID();
        when(digestPort.findById(digestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newsService.getDigest(digestId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- generateDigestForUser ---

    @Test
    void generateDigestForUser_skips_whenDigestAlreadyExistsToday() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(true);

        Digest result = newsService.generateDigestForUser(testUser);

        assertThat(result).isNull();
        verifyNoInteractions(aiService);
    }

    @Test
    void generateDigestForUser_skips_whenNoPreferences() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.empty());

        Digest result = newsService.generateDigestForUser(testUser);

        assertThat(result).isNull();
        verifyNoInteractions(aiService);
    }

    @Test
    void generateDigestForUser_skips_whenTopicsEmpty() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        UserPreferences prefs = UserPreferences.builder().topics(new String[0]).build();
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.of(prefs));

        Digest result = newsService.generateDigestForUser(testUser);

        assertThat(result).isNull();
        verifyNoInteractions(aiService);
    }

    @Test
    void generateDigestForUser_generatesAndSavesDigest() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        UserPreferences prefs = UserPreferences.builder()
                .topics(new String[]{"Reasoning & Context", "Multimodal (Vision, Audio, Video)"})
                .build();
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.of(prefs));

        List<NewsSummary> summaries1 = List.of(NewsSummary.builder().headline("Safety News").build());
        List<NewsSummary> summaries2 = List.of(NewsSummary.builder().headline("DL News").build());
        when(aiService.generateNewsSummaries("Reasoning & Context")).thenReturn(summaries1);
        when(aiService.generateNewsSummaries("Multimodal (Vision, Audio, Video)")).thenReturn(summaries2);

        Digest savedDigest = Digest.builder().id(UUID.randomUUID()).user(testUser).content(Map.of()).build();
        when(digestPort.save(any())).thenReturn(savedDigest);

        Digest result = newsService.generateDigestForUser(testUser);

        assertThat(result).isNotNull();

        ArgumentCaptor<Digest> captor = ArgumentCaptor.forClass(Digest.class);
        verify(digestPort).save(captor.capture());
        Digest captured = captor.getValue();
        assertThat(captured.getUser()).isEqualTo(testUser);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allSummaries = (List<Map<String, Object>>) captured.getContent().get("summaries");
        assertThat(allSummaries).hasSize(2);

        @SuppressWarnings("unchecked")
        List<String> generatedFor = (List<String>) captured.getContent().get("generatedFor");
        assertThat(generatedFor).containsExactly("Reasoning & Context", "Multimodal (Vision, Audio, Video)");
    }

    @Test
    void generateDigestForUser_continuesOnPartialTopicFailure() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        UserPreferences prefs = UserPreferences.builder()
                .topics(new String[]{"topic1", "topic2"})
                .build();
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.of(prefs));

        when(aiService.generateNewsSummaries("topic1")).thenThrow(new RuntimeException("API error"));
        when(aiService.generateNewsSummaries("topic2")).thenReturn(List.of(NewsSummary.builder().headline("News").build()));

        Digest savedDigest = Digest.builder().id(UUID.randomUUID()).user(testUser).content(Map.of()).build();
        when(digestPort.save(any())).thenReturn(savedDigest);

        Digest result = newsService.generateDigestForUser(testUser);

        assertThat(result).isNotNull();
        verify(digestPort).save(any());
    }

    @Test
    void generateDigestForUser_returnsNull_whenAllTopicsFail() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        UserPreferences prefs = UserPreferences.builder()
                .topics(new String[]{"topic1"})
                .build();
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.of(prefs));
        when(aiService.generateNewsSummaries("topic1")).thenThrow(new RuntimeException("fail"));

        Digest result = newsService.generateDigestForUser(testUser);

        assertThat(result).isNull();
        verify(digestPort, never()).save(any());
    }

    // The old generateDigestsForAllUsers bulk method was dead code (the real
    // scheduled path is DigestGenerationJob's chunked run) and has been removed.
}
