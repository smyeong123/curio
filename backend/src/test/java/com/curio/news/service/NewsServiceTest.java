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

    private static NewsSummary story(String headline, String url, String... alsoUrls) {
        List<com.curio.news.dto.SourceLink> also = new ArrayList<>();
        for (String u : alsoUrls) also.add(new com.curio.news.dto.SourceLink("Other", u));
        return NewsSummary.builder().headline(headline).sourceUrl(url).alsoCoveredBy(also).build();
    }

    @SuppressWarnings("unchecked")
    private List<String> savedHeadlines() {
        ArgumentCaptor<Digest> captor = ArgumentCaptor.forClass(Digest.class);
        verify(digestPort).save(captor.capture());
        return ((List<Map<String, Object>>) captor.getValue().getContent().get("summaries")).stream()
                .map(m -> (String) m.get("headline")).toList();
    }

    @Test
    void generate_skipsAStoryAlreadyPickedUnderAnotherTopic_andLetsThatTopicsNextStoryIn() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.of(
                UserPreferences.builder().topics(new String[]{"Claude", "New Models"}).build()));
        when(aiService.generateNewsSummaries(eq("Claude"), eq(Language.EN), isNull())).thenReturn(List.of(
                story("Claude launch", "https://www.anthropic.com/news/launch")));
        when(aiService.generateNewsSummaries(eq("New Models"), eq(Language.EN), isNull())).thenReturn(List.of(
                // Same launch via another outlet that lists the Anthropic post as "also covered by".
                story("Same launch elsewhere", "https://news.example.com/claude", "https://anthropic.com/news/launch/"),
                story("Other model", "https://mistral.ai/news/x")));
        when(digestPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        newsService.generate(testUser, DigestProgressListener.NOOP);

        assertThat(savedHeadlines()).containsExactly("Claude launch", "Other model");
    }

    @Test
    void generate_skipsStoriesTheReaderGotInARecentDigest() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.of(
                UserPreferences.builder().topics(new String[]{"Claude"}).build()));
        Digest yesterday = Digest.builder().user(testUser).content(Map.of("summaries", List.of(
                Map.of("headline", "Old", "source_url", "https://www.anthropic.com/news/old"),
                Map.of("headline", "Older", "source_url", "https://x.example/a",
                        "also_covered_by", List.of(Map.of("name", "B", "url", "https://b.example/story")))))).build();
        when(digestPort.findByUserIdAndGeneratedAtGreaterThanEqualAndGeneratedAtLessThan(eq(userId), any(), any()))
                .thenReturn(List.of(yesterday));
        when(aiService.generateNewsSummaries(eq("Claude"), eq(Language.EN), isNull())).thenReturn(List.of(
                story("Repeat", "https://anthropic.com/news/old?utm_source=x"),
                story("Repeat via another outlet", "https://c.example/z", "https://b.example/story"),
                story("New", "https://www.anthropic.com/news/new")));
        when(digestPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        newsService.generate(testUser, DigestProgressListener.NOOP);

        assertThat(savedHeadlines()).containsExactly("New");
    }

    // --- generate: the same story from another outlet (different URL); real production text ---

    private static NewsSummary told(String headline, String summary, String url) {
        return NewsSummary.builder().headline(headline).summary(summary).sourceUrl(url).build();
    }

    private static final String MISTRAL_SENT_HEADLINE =
            "Mistral Releases 'Le Chonk' — A Massive Open-Weight AI Anyone Can Download";
    private static final String MISTRAL_SENT_SUMMARY = "French AI lab Mistral released Mistral Large 4, nicknamed "
            + "'Le Chonk,' as a free, open-weight model — meaning anyone can download and run it. It has 1 trillion "
            + "parameters (think of those as the knobs that make the model smarter) and handles text and images. "
            + "Mistral says it beats other freely available models at coding tasks and was built entirely in Europe.";
    private static final NewsSummary MISTRAL_ELSEWHERE = told(
            "Mistral Large 4 ships as open-weight, trillion-parameter model targeting cybersecurity",
            "Mistral released Large 4 (ML4, 'Le Chonk') on October 29 — a ~1T-parameter MoE model with 52B active "
                    + "parameters per token, 1M-token context window, and support for 160+ languages. Weights are on "
                    + "Hugging Face under a modified MIT-style license; it runs on as few as four Nvidia B200/B300 GPUs. "
                    + "API pricing is $0.70/M input and $2.10/M output tokens. Primary pitch is cybersecurity: "
                    + "vulnerability detection, patching, and agentic coding.",
            "https://cryptobriefing.com/mistral-ai-large-4-cybersecurity-model/");
    private static final NewsSummary IPO = told(
            "Anthropic Files S-1 for IPO on Nasdaq Under Ticker 'ANT'",
            "Anthropic, PBC filed an S-1 registration statement with the SEC for a proposed IPO of Class A common "
                    + "stock, to be listed on the Nasdaq Global Select Market under ticker 'ANT'. Share count and "
                    + "price range have not been determined. Goldman Sachs and Morgan Stanley are underwriters.",
            "https://www.anthropic.com/news/s1-sec");
    private static final NewsSummary BACKDOOR_PHORION = told(
            "Backdoor Found in Official Claude Code VS Code Extension Versions 2.1.312 and 2.1.313 on Cursor Marketplace",
            "Phorion discovered a supply-chain compromise in the official Claude Code for VS Code extension on the "
                    + "Cursor Marketplace. Versions 2.1.312 and 2.1.313 contained a persistent SSH backdoor targeting "
                    + "macOS, Linux, and Windows — dropping SSH keys and persistence logic on install. Cursor has "
                    + "removed both versions.",
            "https://phorion.io/blog/offical-claude-code-vsx-compromise/");
    private static final NewsSummary BACKDOOR_HACKER_NEWS = told(
            "Fake Claude Coding Tool Installed Spy Software on Developers' Computers",
            "A malicious version of a popular Claude coding extension appeared on the Cursor app marketplace. "
                    + "Developers who downloaded it unknowingly installed a backdoor — a hidden program that lets "
                    + "outsiders access their computer remotely. Anthropic was not directly hacked, but attackers "
                    + "impersonated their official tool. The fake extension has since been removed, but any developer "
                    + "who installed it may still be compromised.",
            "https://thehackernews.com/2026/10/official-claude-code-extension-on.html");
    private static final NewsSummary ENTERPRISE_REPORTS = told(
            "Claude Enterprise Gets Smart Reports, In-App Surveys, and Pooled Group Budgets",
            "Anthropic has made three new features generally available for Claude Enterprise: Smart Reports analyze "
                    + "team transcripts to surface workstreams, spend per workflow, friction points, and repeatable "
                    + "workflow recommendations; in-app surveys capture user feedback on Claude's impact; and pooled "
                    + "group budgets let admins set one shared spending limit per team.",
            "https://claude.com/resources/articles/smart-reports-surveys-pooled-budgets");
    private static final NewsSummary GOV_DISCOUNT = told(
            "Anthropic Gives Government Agencies 40% Discount on Claude Enterprise, API Across All Product Surfaces",
            "Anthropic expanded its GSA partnership to give US federal, state, local, tribal, and territorial "
                    + "government agencies a 40% discount on Claude Enterprise, Claude for Government, and the Claude "
                    + "API — covering direct purchases and cloud-provider channels. No minimum spend commitment; "
                    + "agencies pay for consumed usage.",
            "https://www.anthropic.com/news/expanding-partnership-gsa");

    @Test
    void generate_skipsAStoryTheReaderGotYesterday_whenAnotherOutletTellsItToday() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.of(
                UserPreferences.builder().topics(new String[]{"New Models"}).build()));
        when(digestPort.findByUserIdAndGeneratedAtGreaterThanEqualAndGeneratedAtLessThan(eq(userId), any(), any()))
                .thenReturn(List.of(Digest.builder().user(testUser).content(Map.of("summaries", List.of(
                        Map.of("headline", MISTRAL_SENT_HEADLINE, "summary", MISTRAL_SENT_SUMMARY,
                                "source_url", "https://mistral.ai/news/mistral-large-4-oss/")))).build()));
        when(aiService.generateNewsSummaries(eq("New Models"), eq(Language.EN), isNull()))
                .thenReturn(List.of(MISTRAL_ELSEWHERE, IPO));
        when(digestPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        newsService.generate(testUser, DigestProgressListener.NOOP);

        assertThat(savedHeadlines()).containsExactly(IPO.getHeadline());
    }

    @Test
    void generate_skipsTheSameStoryFromAnotherOutletUnderAnotherTopic_butKeepsOtherStoriesAboutTheSameVendor() {
        Map<String, List<NewsSummary>> byTopic = new LinkedHashMap<>();
        byTopic.put("Claude", List.of(BACKDOOR_PHORION, ENTERPRISE_REPORTS));
        byTopic.put("AI Safety", List.of(BACKDOOR_HACKER_NEWS, GOV_DISCOUNT));
        givenTopics(byTopic);

        newsService.generate(testUser, DigestProgressListener.NOOP);

        // The Hacker News copy of the backdoor is the repeat; AI Safety's slot goes to its next story.
        assertThat(savedHeadlines()).containsExactly(
                BACKDOOR_PHORION.getHeadline(), GOV_DISCOUNT.getHeadline(), ENTERPRISE_REPORTS.getHeadline());
    }

    @Test
    void generate_writesNothing_whenEveryStoryWasAlreadySent() {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.of(
                UserPreferences.builder().topics(new String[]{"Claude"}).build()));
        when(digestPort.findByUserIdAndGeneratedAtGreaterThanEqualAndGeneratedAtLessThan(eq(userId), any(), any()))
                .thenReturn(List.of(Digest.builder().user(testUser).content(Map.of("summaries", List.of(
                        Map.of("source_url", "https://www.anthropic.com/news/old")))).build()));
        when(aiService.generateNewsSummaries(eq("Claude"), eq(Language.EN), isNull())).thenReturn(List.of(
                story("Repeat", "https://www.anthropic.com/news/old")));

        DigestGeneration result = newsService.generate(testUser, DigestProgressListener.NOOP);

        assertThat(result.digest()).isNull();
        verify(digestPort, never()).save(any());
    }

    // --- generate: ranking by importance across topics ---

    private static NewsSummary rated(String headline, int importance) {
        return NewsSummary.builder().headline(headline)
                .sourceUrl("https://example.com/" + headline.replace(' ', '-')).importance(importance).build();
    }

    private void givenTopics(Map<String, List<NewsSummary>> byTopic) {
        when(digestPort.existsByUserIdAndGeneratedAtBetween(eq(userId), any(), any())).thenReturn(false);
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.of(
                UserPreferences.builder().topics(byTopic.keySet().toArray(String[]::new)).build()));
        byTopic.forEach((topic, stories) ->
                when(aiService.generateNewsSummaries(eq(topic), eq(Language.EN), isNull())).thenReturn(stories));
        when(digestPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void generate_ordersTheDigestByImportance_acrossTopics() {
        Map<String, List<NewsSummary>> byTopic = new LinkedHashMap<>();
        byTopic.put("Minor", List.of(rated("Minor tweak", 2), rated("Minor fix", 1)));
        byTopic.put("Major", List.of(rated("Big launch", 5), rated("Price cut", 4)));
        givenTopics(byTopic);

        newsService.generate(testUser, DigestProgressListener.NOOP);

        assertThat(savedHeadlines()).containsExactly("Big launch", "Price cut", "Minor tweak", "Minor fix");
    }

    @Test
    void generate_keepsOneStoryPerTopic_evenWhenAnotherTopicCouldFillTheCap() {
        List<NewsSummary> major = new ArrayList<>();
        for (int i = 1; i <= 9; i++) major.add(rated("Major " + i, 5));
        Map<String, List<NewsSummary>> byTopic = new LinkedHashMap<>();
        byTopic.put("Major", major);
        byTopic.put("Minor", List.of(rated("Minor one", 1)));
        givenTopics(byTopic);

        newsService.generate(testUser, DigestProgressListener.NOOP);

        assertThat(savedHeadlines())
                .containsExactly("Major 1", "Major 2", "Major 3", "Major 4", "Major 5", "Major 6", "Major 7", "Minor one");
    }

    @Test
    void generate_withMoreTopicsThanTheCap_letsTheBigStoryIn_andDropsTheLastTopicsOfEqualImportance() {
        Map<String, List<NewsSummary>> byTopic = new LinkedHashMap<>();
        for (int i = 1; i <= 9; i++) byTopic.put("Topic " + i, List.of(rated("Story " + i, 3)));
        byTopic.put("Topic 10", List.of(rated("Big launch", 5)));
        givenTopics(byTopic);

        newsService.generate(testUser, DigestProgressListener.NOOP);

        assertThat(savedHeadlines()).containsExactly(
                "Big launch", "Story 1", "Story 2", "Story 3", "Story 4", "Story 5", "Story 6", "Story 7");
    }

    @Test
    void generate_replacesASkippedRepeatWithThatTopicsNextStory_andKeepsTheMoreImportantCopyOfAStory() {
        when(digestPort.findByUserIdAndGeneratedAtGreaterThanEqualAndGeneratedAtLessThan(eq(userId), any(), any()))
                .thenReturn(List.of(Digest.builder().user(testUser).content(Map.of("summaries", List.of(
                        Map.of("source_url", "https://example.com/Sent-yesterday")))).build()));
        Map<String, List<NewsSummary>> byTopic = new LinkedHashMap<>();
        byTopic.put("Claude", List.of(
                rated("Sent yesterday", 5),
                story("Launch, minor angle", "https://anthropic.com/news/launch"),
                rated("Claude next", 2)));
        byTopic.put("New Models", List.of(
                NewsSummary.builder().headline("Launch").sourceUrl("https://www.anthropic.com/news/launch/")
                        .importance(4).build()));
        givenTopics(byTopic);

        newsService.generate(testUser, DigestProgressListener.NOOP);

        // "Sent yesterday" is a repeat, and the 4-rated copy of the launch beats the unrated
        // (neutral 3) one, so Claude's slot goes to its next story.
        assertThat(savedHeadlines()).containsExactly("Launch", "Claude next");
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_stampsTheNeutralImportanceOnUnratedStories_andClampsOutOfRangeOnes() {
        Map<String, List<NewsSummary>> byTopic = new LinkedHashMap<>();
        byTopic.put("Claude", List.of(
                story("Unrated (old cache)", "https://a.example/1"),
                rated("Overrated", 9),
                rated("Underrated", -2)));
        givenTopics(byTopic);

        newsService.generate(testUser, DigestProgressListener.NOOP);

        ArgumentCaptor<Digest> captor = ArgumentCaptor.forClass(Digest.class);
        verify(digestPort).save(captor.capture());
        List<Map<String, Object>> saved = (List<Map<String, Object>>) captor.getValue().getContent().get("summaries");
        assertThat(saved).extracting(m -> m.get("headline"), m -> m.get("importance")).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Overrated", 5),
                org.assertj.core.groups.Tuple.tuple("Unrated (old cache)", 3),
                org.assertj.core.groups.Tuple.tuple("Underrated", 1));
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
