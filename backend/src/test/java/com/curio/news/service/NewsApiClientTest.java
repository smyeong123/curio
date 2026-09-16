package com.curio.news.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
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
class NewsApiClientTest {

    @Mock private RestTemplate restTemplate;
    @Mock private RestTemplateBuilder restTemplateBuilder;
    @Mock private LabBlogFetcher labBlogFetcher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private NewsApiClient newsApiClient;

    @BeforeEach
    void setUp() {
        when(restTemplateBuilder.setConnectTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);

        newsApiClient = new NewsApiClient(objectMapper, restTemplateBuilder, labBlogFetcher);
        ReflectionTestUtils.setField(newsApiClient, "newsApiKey", "test-news-key");
        ReflectionTestUtils.setField(newsApiClient, "newsApiUrl", "https://newsapi.org/v2/everything");
        newsApiClient.init();
    }

    // --- fetchNewsArticles ---

    @Test
    void fetchNewsArticles_returnsEmpty_whenApiKeyIsBlank() {
        ReflectionTestUtils.setField(newsApiClient, "newsApiKey", "");

        List<Map<String, String>> result = newsApiClient.fetchNewsArticles("technology");

        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void fetchNewsArticles_returnsEmpty_whenApiKeyIsNull() {
        ReflectionTestUtils.setField(newsApiClient, "newsApiKey", null);

        List<Map<String, String>> result = newsApiClient.fetchNewsArticles("technology");

        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void fetchNewsArticles_parsesArticlesFromApiResponse() throws Exception {
        String responseBody = objectMapper.writeValueAsString(Map.of(
                "articles", List.of(
                        Map.of(
                                "title", "Breaking News",
                                "description", "Something happened",
                                "url", "https://example.com/news",
                                "source", Map.of("name", "Example News"),
                                "publishedAt", "2026-03-03T10:00:00Z"
                        )
                )
        ));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        List<Map<String, String>> result = newsApiClient.fetchNewsArticles("technology");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("title", "Breaking News");
        assertThat(result.get(0)).containsEntry("description", "Something happened");
        assertThat(result.get(0)).containsEntry("url", "https://example.com/news");
        assertThat(result.get(0)).containsEntry("sourceName", "Example News");
        assertThat(result.get(0)).containsEntry("publishedAt", "2026-03-03T10:00:00Z");
    }

    @Test
    void fetchNewsArticles_skipsArticlesWithBlankTitle() throws Exception {
        String responseBody = objectMapper.writeValueAsString(Map.of(
                "articles", List.of(
                        Map.of("title", "", "description", "no title", "url", "https://ex.com",
                                "source", Map.of("name", "Ex"), "publishedAt", "2026-03-03T10:00:00Z"),
                        Map.of("title", "Valid Title", "description", "has title", "url", "https://ex.com/2",
                                "source", Map.of("name", "Ex"), "publishedAt", "2026-03-03T10:00:00Z")
                )
        ));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        List<Map<String, String>> result = newsApiClient.fetchNewsArticles("technology");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("title", "Valid Title");
    }

    @Test
    void fetchNewsArticles_returnsEmpty_whenHttpCallThrows() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(new RuntimeException("connection refused"));

        List<Map<String, String>> result = newsApiClient.fetchNewsArticles("technology");

        assertThat(result).isEmpty();
    }

    // --- parseNewsApiArticles ---

    @Test
    void parseNewsApiArticles_returnsEmpty_whenInputIsNull() {
        assertThat(newsApiClient.parseNewsApiArticles(null)).isEmpty();
    }

    @Test
    void parseNewsApiArticles_returnsEmpty_whenInputIsBlank() {
        assertThat(newsApiClient.parseNewsApiArticles("  ")).isEmpty();
    }

    @Test
    void parseNewsApiArticles_returnsEmpty_whenArticlesNodeIsMissing() {
        assertThat(newsApiClient.parseNewsApiArticles("{\"status\":\"ok\"}")).isEmpty();
    }

    @Test
    void parseNewsApiArticles_returnsEmpty_whenInputIsInvalidJson() {
        assertThat(newsApiClient.parseNewsApiArticles("not-json")).isEmpty();
    }

    // --- buildSourceContext ---

    @Test
    void buildSourceContext_returnsPlaceholder_whenListIsEmpty() {
        String result = newsApiClient.buildSourceContext(List.of());

        assertThat(result).contains("No external source articles");
    }

    @Test
    void buildSourceContext_buildsNumberedListFromArticles() {
        List<Map<String, String>> articles = List.of(
                Map.of("title", "First Article", "sourceName", "BBC", "url", "https://bbc.com/1",
                        "publishedAt", "2026-03-03", "description", "About first"),
                Map.of("title", "Second Article", "sourceName", "CNN", "url", "https://cnn.com/2",
                        "publishedAt", "2026-03-03", "description", "About second")
        );

        String result = newsApiClient.buildSourceContext(articles);

        assertThat(result).contains("1. Title: First Article");
        assertThat(result).contains("Source: BBC");
        assertThat(result).contains("URL: https://bbc.com/1");
        assertThat(result).contains("2. Title: Second Article");
        assertThat(result).contains("Source: CNN");
    }
}
