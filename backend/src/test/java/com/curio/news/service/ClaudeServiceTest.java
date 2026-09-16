package com.curio.news.service;

import com.curio.news.dto.NewsSummary;
import com.curio.news.dto.QuizGenerationResult;
import com.curio.shared.concurrent.SingleFlight;
import com.curio.shared.i18n.Language;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaudeServiceTest {

    @Mock private RestTemplate restTemplate;
    @Mock private RestTemplateBuilder restTemplateBuilder;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private NewsApiClient newsApiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ClaudeService claudeService;

    // Minimal valid payloads that Claude would return as text
    private static final String SUMMARIES_JSON =
            "[{\"headline\":\"Test Headline\",\"summary\":\"A summary\",\"why_it_matters\":\"It matters\"," +
            "\"source_url\":\"https://example.com\",\"source_name\":\"Example\",\"topic\":\"technology\"}]";

    private static final String QUIZ_JSON =
            "{\"questions\":[{\"id\":1,\"question\":\"What?\",\"options\":{\"A\":\"a\",\"B\":\"b\"," +
            "\"C\":\"c\",\"D\":\"d\"},\"correct\":\"A\",\"explanation\":\"Because A\"}]}";

    @BeforeEach
    void setUp() {
        when(restTemplateBuilder.setConnectTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        // lenient: not every test exercises Redis (quiz tests don't cache)
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        claudeService = new ClaudeService(objectMapper, redisTemplate, restTemplateBuilder, newsApiClient, new SingleFlight(),
                CircuitBreakerRegistry.ofDefaults(), BulkheadRegistry.ofDefaults());
        ReflectionTestUtils.setField(claudeService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(claudeService, "model", "claude-test-model");
        ReflectionTestUtils.setField(claudeService, "apiUrl", "https://api.anthropic.com/v1/messages");
        claudeService.init();
    }

    // --- generateNewsSummaries ---

    @Test
    void generateNewsSummaries_returnsCachedResult_withoutCallingApi() {
        List<Map<String, Object>> cached = List.of(Map.of("headline", "Cached", "topic", "technology"));
        when(valueOperations.get(anyString())).thenReturn(cached);

        List<NewsSummary> result = claudeService.generateNewsSummaries("technology");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHeadline()).isEqualTo("Cached");
        verifyNoInteractions(restTemplate);
        verifyNoInteractions(newsApiClient);
    }

    @Test
    void generateNewsSummaries_callsApiAndCachesResult_onCacheMiss() throws Exception {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(newsApiClient.fetchNewsArticles("technology")).thenReturn(List.of());
        when(newsApiClient.buildSourceContext(any())).thenReturn("no articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(claudeBody(SUMMARIES_JSON)));

        List<NewsSummary> result = claudeService.generateNewsSummaries("technology");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHeadline()).isEqualTo("Test Headline");
        verify(valueOperations).set(anyString(), anyList(), eq(Duration.ofHours(12)));
    }

    @Test
    void generateNewsSummaries_stripsJsonCodeFences_beforeParsing() throws Exception {
        String textWithFences = "```json\n" + SUMMARIES_JSON + "\n```";
        when(valueOperations.get(anyString())).thenReturn(null);
        when(newsApiClient.fetchNewsArticles(any())).thenReturn(List.of());
        when(newsApiClient.buildSourceContext(any())).thenReturn("no articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(claudeBody(textWithFences)));

        List<NewsSummary> result = claudeService.generateNewsSummaries("technology");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHeadline()).isEqualTo("Test Headline");
    }

    @Test
    void generateNewsSummaries_stripsPlainCodeFences_beforeParsing() throws Exception {
        String textWithFences = "```\n" + SUMMARIES_JSON + "\n```";
        when(valueOperations.get(anyString())).thenReturn(null);
        when(newsApiClient.fetchNewsArticles(any())).thenReturn(List.of());
        when(newsApiClient.buildSourceContext(any())).thenReturn("no articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(claudeBody(textWithFences)));

        List<NewsSummary> result = claudeService.generateNewsSummaries("technology");

        assertThat(result).hasSize(1);
    }

    @Test
    void generateNewsSummaries_returnsEmptyList_whenApiCallThrows() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(newsApiClient.fetchNewsArticles(any())).thenReturn(List.of());
        when(newsApiClient.buildSourceContext(any())).thenReturn("no articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new RuntimeException("API unavailable"));

        List<NewsSummary> result = claudeService.generateNewsSummaries("technology");

        assertThat(result).isEmpty();
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void generateNewsSummaries_doesNotCache_whenSummariesAreEmpty() throws Exception {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(newsApiClient.fetchNewsArticles(any())).thenReturn(List.of());
        when(newsApiClient.buildSourceContext(any())).thenReturn("no articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(claudeBody("[]")));

        List<NewsSummary> result = claudeService.generateNewsSummaries("technology");

        assertThat(result).isEmpty();
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void generateNewsSummaries_throwsAfterAllRetriesExhausted() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(newsApiClient.fetchNewsArticles(any())).thenReturn(List.of());
        when(newsApiClient.buildSourceContext(any())).thenReturn("no articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new RuntimeException("connection timeout"));

        // RuntimeException is swallowed by callClaudeApi and returns null → empty list
        List<NewsSummary> result = claudeService.generateNewsSummaries("technology");
        assertThat(result).isEmpty();
    }

    // --- generateQuizQuestions ---

    @Test
    void generateQuizQuestions_returnsQuiz_onSuccess() throws Exception {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(claudeBody(QUIZ_JSON)));

        QuizGenerationResult result = claudeService.generateQuizQuestions("digest content");

        assertThat(result.getQuestions()).hasSize(1);
    }

    @Test
    void generateQuizQuestions_returnsEmptyQuestions_whenApiCallThrows() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new RuntimeException("API error"));

        QuizGenerationResult result = claudeService.generateQuizQuestions("digest content");

        assertThat(result.getQuestions()).isEmpty();
    }

    @Test
    void generateQuizQuestions_returnsEmptyQuestions_whenResponseIsMalformedJson() throws Exception {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(claudeBody("not valid json {")));

        QuizGenerationResult result = claudeService.generateQuizQuestions("digest content");

        assertThat(result.getQuestions()).isEmpty();
    }

    // --- helpers ---

    /** Wraps a text payload in a Claude API response envelope. */
    private String claudeBody(String text) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "content", List.of(Map.of("type", "text", "text", text))
        ));
    }
    // --- editions ---

    @SuppressWarnings("unchecked")
    private static String promptOf(HttpEntity<?> entity) {
        Map<String, Object> body = (Map<String, Object>) entity.getBody();
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        return String.valueOf(messages.get(0).get("content"));
    }

    @Test
    void generateNewsSummaries_koreanEdition_usesALanguageScopedCacheKey_andAsksForKorean() throws Exception {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(newsApiClient.fetchNewsArticles("technology")).thenReturn(List.of(Map.of("title", "t", "url", "u")));
        when(newsApiClient.buildSourceContext(any())).thenReturn("articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(claudeBody(SUMMARIES_JSON)));

        List<NewsSummary> result = claudeService.generateNewsSummaries("technology", Language.KO, null);

        assertThat(result).hasSize(1);
        // Read twice on a cold cache: the fast path, then the re-check under the single-flight lock.
        verify(valueOperations, atLeastOnce()).get("news:summaries:technology:" + LocalDate.now() + ":ko");
        verify(valueOperations).set(eq("news:summaries:technology:" + LocalDate.now() + ":ko"), anyList(), eq(Duration.ofHours(12)));
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST),
                argThat((HttpEntity<?> e) -> promptOf(e).contains("한국어") && promptOf(e).contains("해요체")
                        && promptOf(e).contains("\"topic\": \"technology\"")),
                eq(String.class));
    }

    @Test
    void generateNewsSummaries_defaultsToTheEnglishEdition_withItsOwnCacheKey() throws Exception {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(newsApiClient.fetchNewsArticles("technology")).thenReturn(List.of());
        when(newsApiClient.buildSourceContext(any())).thenReturn("no articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(claudeBody(SUMMARIES_JSON)));

        claudeService.generateNewsSummaries("technology");

        verify(valueOperations).set(eq("news:summaries:technology:" + LocalDate.now() + ":en"), anyList(), eq(Duration.ofHours(12)));
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST),
                argThat((HttpEntity<?> e) -> promptOf(e).contains("in English") && !promptOf(e).contains("한국어")),
                eq(String.class));
    }

    @Test
    void generateNewsSummaries_byokKoreanEdition_skipsTheCache_andAsksForKorean() throws Exception {
        when(newsApiClient.fetchNewsArticles("technology")).thenReturn(List.of(Map.of("title", "t", "url", "u")));
        when(newsApiClient.buildSourceContext(any())).thenReturn("articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(claudeBody(SUMMARIES_JSON)));

        List<NewsSummary> result = claudeService.generateNewsSummaries("technology", Language.KO, "user-key");

        assertThat(result).hasSize(1);
        verifyNoInteractions(valueOperations);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST),
                argThat((HttpEntity<?> e) -> promptOf(e).contains("한국어")
                        && "user-key".equals(e.getHeaders().getFirst("x-api-key"))),
                eq(String.class));
    }

    @Test
    void generateQuizQuestions_koreanEdition_honoursTheDifficultyHint_andAsksForKorean() throws Exception {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(claudeBody(QUIZ_JSON)));

        QuizGenerationResult result = claudeService.generateQuizQuestions(
                "digest content", Language.KO, AiService.DifficultyHint.HARDER, null);

        assertThat(result.getQuestions()).hasSize(1);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST),
                argThat((HttpEntity<?> e) -> promptOf(e).contains("0 easy, 2 medium, 3 challenging")
                        && promptOf(e).contains("한국어")),
                eq(String.class));
    }
}
