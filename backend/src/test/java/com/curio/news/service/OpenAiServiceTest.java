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
class OpenAiServiceTest {

    @Mock private RestTemplate restTemplate;
    @Mock private RestTemplateBuilder restTemplateBuilder;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private NewsApiClient newsApiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OpenAiService openAiService;

    private static final String SUMMARIES_JSON =
            "[{\"headline\":\"OpenAI Headline\",\"summary\":\"A summary\",\"why_it_matters\":\"It matters\"," +
            "\"source_url\":\"https://example.com\",\"source_name\":\"Example\",\"topic\":\"business\"}]";

    private static final String QUIZ_JSON =
            "{\"questions\":[{\"id\":1,\"question\":\"What?\",\"options\":{\"A\":\"a\",\"B\":\"b\"," +
            "\"C\":\"c\",\"D\":\"d\"},\"correct\":\"C\",\"explanation\":\"Because C\"}]}";

    @BeforeEach
    void setUp() {
        when(restTemplateBuilder.setConnectTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        // lenient: not every test exercises Redis (quiz tests don't cache)
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        openAiService = new OpenAiService(objectMapper, redisTemplate, restTemplateBuilder, newsApiClient, new SingleFlight(),
                CircuitBreakerRegistry.ofDefaults(), BulkheadRegistry.ofDefaults());
        ReflectionTestUtils.setField(openAiService, "apiKey", "test-openai-key");
        ReflectionTestUtils.setField(openAiService, "apiUrl", "https://api.openai.com/v1/chat/completions");
        ReflectionTestUtils.setField(openAiService, "model", "gpt-4o-mini");
        openAiService.init();
    }

    // --- generateNewsSummaries ---

    @Test
    void generateNewsSummaries_returnsCachedResult_withoutCallingApi() {
        List<Map<String, Object>> cached = List.of(Map.of("headline", "Cached OpenAI", "topic", "business"));
        when(valueOperations.get(anyString())).thenReturn(cached);

        List<NewsSummary> result = openAiService.generateNewsSummaries("business");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHeadline()).isEqualTo("Cached OpenAI");
        verifyNoInteractions(restTemplate);
        verifyNoInteractions(newsApiClient);
    }

    @Test
    void generateNewsSummaries_callsOpenAiAndCachesResult_onCacheMiss() throws Exception {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(newsApiClient.fetchNewsArticles("business")).thenReturn(List.of());
        when(newsApiClient.buildSourceContext(any())).thenReturn("no articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(openAiBody(SUMMARIES_JSON)));

        List<NewsSummary> result = openAiService.generateNewsSummaries("business");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHeadline()).isEqualTo("OpenAI Headline");
        verify(valueOperations).set(anyString(), anyList(), eq(Duration.ofHours(12)));
    }

    @Test
    void generateNewsSummaries_sendsBearerAuthHeader() throws Exception {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(newsApiClient.fetchNewsArticles(any())).thenReturn(List.of());
        when(newsApiClient.buildSourceContext(any())).thenReturn("no articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenAnswer(invocation -> {
                    HttpEntity<?> entity = invocation.getArgument(2);
                    assertThat(entity.getHeaders().getFirst("Authorization"))
                            .isEqualTo("Bearer test-openai-key");
                    return ResponseEntity.ok(openAiBody(SUMMARIES_JSON));
                });

        openAiService.generateNewsSummaries("business");
    }

    @Test
    void generateNewsSummaries_stripsJsonCodeFences_beforeParsing() throws Exception {
        String textWithFences = "```json\n" + SUMMARIES_JSON + "\n```";
        when(valueOperations.get(anyString())).thenReturn(null);
        when(newsApiClient.fetchNewsArticles(any())).thenReturn(List.of());
        when(newsApiClient.buildSourceContext(any())).thenReturn("no articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(openAiBody(textWithFences)));

        List<NewsSummary> result = openAiService.generateNewsSummaries("business");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHeadline()).isEqualTo("OpenAI Headline");
    }

    @Test
    void generateNewsSummaries_returnsEmptyList_whenApiCallThrows() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(newsApiClient.fetchNewsArticles(any())).thenReturn(List.of());
        when(newsApiClient.buildSourceContext(any())).thenReturn("no articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new RuntimeException("OpenAI unavailable"));

        List<NewsSummary> result = openAiService.generateNewsSummaries("business");

        assertThat(result).isEmpty();
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void generateNewsSummaries_doesNotCache_whenSummariesAreEmpty() throws Exception {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(newsApiClient.fetchNewsArticles(any())).thenReturn(List.of());
        when(newsApiClient.buildSourceContext(any())).thenReturn("no articles");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(openAiBody("[]")));

        List<NewsSummary> result = openAiService.generateNewsSummaries("business");

        assertThat(result).isEmpty();
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    // --- generateQuizQuestions ---

    @Test
    void generateQuizQuestions_returnsQuiz_onSuccess() throws Exception {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(openAiBody(QUIZ_JSON)));

        QuizGenerationResult result = openAiService.generateQuizQuestions("digest content");

        assertThat(result.getQuestions()).hasSize(1);
    }

    @Test
    void generateQuizQuestions_returnsEmptyQuestions_whenApiCallThrows() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new RuntimeException("API error"));

        QuizGenerationResult result = openAiService.generateQuizQuestions("digest content");

        assertThat(result.getQuestions()).isEmpty();
    }

    @Test
    void generateQuizQuestions_returnsEmptyQuestions_whenResponseIsMalformedJson() throws Exception {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(openAiBody("not json {")));

        QuizGenerationResult result = openAiService.generateQuizQuestions("digest content");

        assertThat(result.getQuestions()).isEmpty();
    }

    // --- helpers ---

    /** Wraps text in an OpenAI chat completions response envelope. */
    private String openAiBody(String text) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("role", "assistant", "content", text)
                ))
        ));
    }
}
