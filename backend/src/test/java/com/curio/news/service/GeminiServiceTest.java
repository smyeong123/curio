package com.curio.news.service;

import com.curio.news.dto.NewsSummary;
import com.curio.news.dto.QuizGenerationResult;
import com.curio.shared.concurrent.SingleFlight;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {

    @Mock private RestTemplate restTemplate;
    @Mock private RestTemplateBuilder restTemplateBuilder;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private NewsApiClient newsApiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GeminiService geminiService;

    private static final String SUMMARIES_JSON =
            "[{\"headline\":\"Gemini Headline\",\"summary\":\"A summary\",\"why_it_matters\":\"It matters\"," +
            "\"source_url\":\"https://example.com\",\"source_name\":\"Example\",\"topic\":\"science\"}]";

    private static final String QUIZ_JSON =
            "{\"questions\":[{\"id\":1,\"question\":\"What?\",\"options\":{\"A\":\"a\",\"B\":\"b\"," +
            "\"C\":\"c\",\"D\":\"d\"},\"correct\":\"B\",\"explanation\":\"Because B\"}]}";

    @BeforeEach
    void setUp() {
        when(restTemplateBuilder.setConnectTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        // lenient: not every test exercises Redis (quiz tests don't cache)
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        geminiService = new GeminiService(objectMapper, redisTemplate, restTemplateBuilder, newsApiClient, new SingleFlight(),
                CircuitBreakerRegistry.ofDefaults(), BulkheadRegistry.ofDefaults());
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-gemini-key");
        ReflectionTestUtils.setField(geminiService, "apiUrl",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent");
        geminiService.init();
    }

    // --- generateNewsSummaries ---

    @Test
    void generateNewsSummaries_returnsCachedResult_withoutCallingApi() {
        List<Map<String, Object>> cached = List.of(Map.of("headline", "Cached Gemini", "topic", "science"));
        when(valueOperations.get(anyString())).thenReturn(cached);

        List<NewsSummary> result = geminiService.generateNewsSummaries("science");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHeadline()).isEqualTo("Cached Gemini");
        verifyNoInteractions(restTemplate);
        verifyNoInteractions(newsApiClient);
    }

    @Test
    void generateNewsSummaries_callsGeminiApiAndCachesResult_onCacheMiss() throws Exception {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(newsApiClient.fetchNewsArticles("science")).thenReturn(List.of());
        when(newsApiClient.buildSourceContext(any())).thenReturn("no articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(geminiBody(SUMMARIES_JSON)));

        List<NewsSummary> result = geminiService.generateNewsSummaries("science");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHeadline()).isEqualTo("Gemini Headline");
        verify(valueOperations).set(anyString(), anyList(), eq(Duration.ofHours(12)));
    }

    @Test
    void generateNewsSummaries_passesApiKeyViaHeader() throws Exception {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(newsApiClient.fetchNewsArticles(any())).thenReturn(List.of());
        when(newsApiClient.buildSourceContext(any())).thenReturn("no articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(geminiBody(SUMMARIES_JSON)));

        geminiService.generateNewsSummaries("science");

        // The key travels in the x-goog-api-key header, never the URL query string
        // (URL keys leak into access logs/proxies).
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        assertThat(entityCaptor.getValue().getHeaders().getFirst("x-goog-api-key"))
                .isEqualTo("test-gemini-key");
    }

    @Test
    void generateNewsSummaries_stripsJsonCodeFences_beforeParsing() throws Exception {
        String textWithFences = "```json\n" + SUMMARIES_JSON + "\n```";
        when(valueOperations.get(anyString())).thenReturn(null);
        when(newsApiClient.fetchNewsArticles(any())).thenReturn(List.of());
        when(newsApiClient.buildSourceContext(any())).thenReturn("no articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(geminiBody(textWithFences)));

        List<NewsSummary> result = geminiService.generateNewsSummaries("science");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHeadline()).isEqualTo("Gemini Headline");
    }

    @Test
    void generateNewsSummaries_returnsEmptyList_whenApiCallThrows() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(newsApiClient.fetchNewsArticles(any())).thenReturn(List.of());
        when(newsApiClient.buildSourceContext(any())).thenReturn("no articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Gemini unavailable"));

        List<NewsSummary> result = geminiService.generateNewsSummaries("science");

        assertThat(result).isEmpty();
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void generateNewsSummaries_doesNotCache_whenSummariesAreEmpty() throws Exception {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(newsApiClient.fetchNewsArticles(any())).thenReturn(List.of());
        when(newsApiClient.buildSourceContext(any())).thenReturn("no articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(geminiBody("[]")));

        List<NewsSummary> result = geminiService.generateNewsSummaries("science");

        assertThat(result).isEmpty();
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    // --- generateQuizQuestions ---

    @Test
    void generateQuizQuestions_returnsQuiz_onSuccess() throws Exception {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(geminiBody(QUIZ_JSON)));

        QuizGenerationResult result = geminiService.generateQuizQuestions("digest content");

        assertThat(result.getQuestions()).hasSize(1);
    }

    @Test
    void generateQuizQuestions_returnsEmptyQuestions_whenApiCallThrows() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new RuntimeException("API error"));

        QuizGenerationResult result = geminiService.generateQuizQuestions("digest content");

        assertThat(result.getQuestions()).isEmpty();
    }

    @Test
    void generateQuizQuestions_returnsEmptyQuestions_whenResponseIsMalformedJson() throws Exception {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(geminiBody("not json {")));

        QuizGenerationResult result = geminiService.generateQuizQuestions("digest content");

        assertThat(result.getQuestions()).isEmpty();
    }

    // --- helpers ---

    /** Wraps text in a Gemini API response envelope. */
    private String geminiBody(String text) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of("text", text))
                        )
                ))
        ));
    }
}
