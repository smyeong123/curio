package com.curio.news.service;

import com.curio.news.port.out.AiService;
import com.curio.news.dto.DigestResponse;
import com.curio.news.dto.NewsSummary;
import com.curio.news.entity.Digest;
import com.curio.news.port.in.DigestGeneration;
import com.curio.news.port.in.DigestProgressListener;
import com.curio.news.port.out.DigestPort;
import com.curio.shared.exception.ResourceNotFoundException;
import com.curio.shared.i18n.Language;
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

    // --- generate: the digest, or null when nothing was written ---

    @Test
    void generate_skips_whenDigestAlreadyExistsToday() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(true);

        Digest result = newsService.generate(testUser, DigestProgressListener.NOOP).digest();

        assertThat(result).isNull();
        verifyNoInteractions(aiService);
    }

    @Test
    void generate_skips_whenNoPreferences() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.empty());

        Digest result = newsService.generate(testUser, DigestProgressListener.NOOP).digest();

        assertThat(result).isNull();
        verifyNoInteractions(aiService);
    }

    @Test
    void generate_skips_whenTopicsEmpty() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        UserPreferences prefs = UserPreferences.builder().topics(new String[0]).build();
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.of(prefs));

        Digest result = newsService.generate(testUser, DigestProgressListener.NOOP).digest();

        assertThat(result).isNull();
        verifyNoInteractions(aiService);
    }

    @Test
    void generate_generatesAndSavesDigest() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        UserPreferences prefs = UserPreferences.builder()
                .topics(new String[]{"Reasoning & Context", "Multimodal (Vision, Audio, Video)"})
                .build();
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.of(prefs));

        List<NewsSummary> summaries1 = List.of(NewsSummary.builder().headline("Safety News").build());
        List<NewsSummary> summaries2 = List.of(NewsSummary.builder().headline("DL News").build());
        when(aiService.generateNewsSummaries(eq("Reasoning & Context"), eq(Language.EN), isNull())).thenReturn(summaries1);
        when(aiService.generateNewsSummaries(eq("Multimodal (Vision, Audio, Video)"), eq(Language.EN), isNull())).thenReturn(summaries2);

        Digest savedDigest = Digest.builder().id(UUID.randomUUID()).user(testUser).content(Map.of()).build();
        when(digestPort.save(any())).thenReturn(savedDigest);

        Digest result = newsService.generate(testUser, DigestProgressListener.NOOP).digest();

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
    void generate_continuesOnPartialTopicFailure() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        UserPreferences prefs = UserPreferences.builder()
                .topics(new String[]{"topic1", "topic2"})
                .build();
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.of(prefs));

        when(aiService.generateNewsSummaries(eq("topic1"), eq(Language.EN), isNull())).thenThrow(new RuntimeException("API error"));
        when(aiService.generateNewsSummaries(eq("topic2"), eq(Language.EN), isNull())).thenReturn(List.of(NewsSummary.builder().headline("News").build()));

        Digest savedDigest = Digest.builder().id(UUID.randomUUID()).user(testUser).content(Map.of()).build();
        when(digestPort.save(any())).thenReturn(savedDigest);

        Digest result = newsService.generate(testUser, DigestProgressListener.NOOP).digest();

        assertThat(result).isNotNull();
        verify(digestPort).save(any());
    }

    @Test
    void generate_returnsNull_whenAllTopicsFail() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        UserPreferences prefs = UserPreferences.builder()
                .topics(new String[]{"topic1"})
                .build();
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.of(prefs));
        when(aiService.generateNewsSummaries(eq("topic1"), eq(Language.EN), isNull())).thenThrow(new RuntimeException("fail"));

        Digest result = newsService.generate(testUser, DigestProgressListener.NOOP).digest();

        assertThat(result).isNull();
        verify(digestPort, never()).save(any());
    }

    @Test
    void generate_writesInTheReadersEdition_andStampsItOnTheDigest() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        UserPreferences prefs = UserPreferences.builder()
                .topics(new String[]{"topic1"})
                .language("ko")
                .build();
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.of(prefs));
        when(aiService.generateNewsSummaries(eq("topic1"), eq(Language.KO), isNull()))
                .thenReturn(List.of(NewsSummary.builder().headline("한국어 헤드라인").build()));
        when(digestPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Digest result = newsService.generate(testUser, DigestProgressListener.NOOP).digest();

        assertThat(result).isNotNull();
        assertThat(result.getContent().get("language")).isEqualTo("ko");
        verify(aiService, never()).generateNewsSummaries(eq("topic1"), eq(Language.EN), any());
    }

    @Test
    void generate_threadsTheByokKeyWithTheEdition() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        when(userPreferencesPort.findByUserId(userId))
                .thenReturn(Optional.of(UserPreferences.builder().topics(new String[]{"topic1"}).build()));
        when(userApiKeyService.resolveDecryptedKey(eq(userId), any())).thenReturn(Optional.of("user-byok-key"));
        when(aiService.generateNewsSummaries(eq("topic1"), eq(Language.EN), eq("user-byok-key")))
                .thenReturn(List.of(NewsSummary.builder().headline("News").build()));
        when(digestPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Digest result = newsService.generate(testUser, DigestProgressListener.NOOP).digest();

        assertThat(result.getContent().get("language")).isEqualTo("en");
    }

    // --- generate: the typed outcome the digest pipeline reads ---

    @Test
    void generate_reportsWhyNothingWasWritten() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(true);
        assertThat(newsService.generate(testUser, null).status()).isEqualTo(DigestGeneration.Status.ALREADY_EXISTS);

        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.empty());
        assertThat(newsService.generate(testUser, null).status()).isEqualTo(DigestGeneration.Status.NO_TOPICS);

        when(userPreferencesPort.findByUserId(userId))
                .thenReturn(Optional.of(UserPreferences.builder().topics(new String[]{"topic1"}).build()));
        when(aiService.generateNewsSummaries(eq("topic1"), eq(Language.EN), isNull())).thenReturn(List.of());
        assertThat(newsService.generate(testUser, null).status()).isEqualTo(DigestGeneration.Status.NOTHING_GENERATED);
        verify(digestPort, never()).save(any());
    }

    @Test
    void generate_treatsALostUniqueIndexRace_asAlreadyExists() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        when(userPreferencesPort.findByUserId(userId))
                .thenReturn(Optional.of(UserPreferences.builder().topics(new String[]{"topic1"}).build()));
        when(aiService.generateNewsSummaries(eq("topic1"), eq(Language.EN), isNull()))
                .thenReturn(List.of(NewsSummary.builder().headline("News").build()));
        when(digestPort.save(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("uq"));

        DigestGeneration outcome = newsService.generate(testUser, null);

        assertThat(outcome.status()).isEqualTo(DigestGeneration.Status.ALREADY_EXISTS);
        assertThat(outcome.digest()).isNull();
    }
}
