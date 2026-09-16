package com.curio.news.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewsApiClient {

    private final ObjectMapper objectMapper;
    private final RestTemplateBuilder restTemplateBuilder;
    private final LabBlogFetcher labBlogFetcher;

    @Value("${news.api-key:}")
    private String newsApiKey;

    @Value("${news.api-url:https://newsapi.org/v2/everything}")
    private String newsApiUrl;

    private RestTemplate restTemplate;

    @PostConstruct
    void init() {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Fetches recent articles for a topic, combining first-party lab RSS feeds
     * (highest signal for release notes) with NewsAPI keyword search
     * (broader coverage). Lab-blog results come first so the AI prompt sees them
     * at the top of the context.
     */
    public List<Map<String, String>> fetchNewsArticles(String topic) {
        List<Map<String, String>> combined = new ArrayList<>(labBlogFetcher.fetchForTopic(topic));
        combined.addAll(fetchFromNewsApi(topic));
        return combined;
    }

    private List<Map<String, String>> fetchFromNewsApi(String topic) {
        if (newsApiKey == null || newsApiKey.isBlank()) {
            return List.of();
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(newsApiUrl)
                    .queryParam("q", topic)
                    .queryParam("language", "en")
                    .queryParam("sortBy", "publishedAt")
                    .queryParam("pageSize", 10)
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Api-Key", newsApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return parseNewsApiArticles(response.getBody());
        } catch (Exception e) {
            log.warn("Failed to fetch articles from News API for topic {}", topic, e);
            return List.of();
        }
    }

    public List<Map<String, String>> parseNewsApiArticles(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode articles = root.path("articles");
            if (!articles.isArray()) {
                return List.of();
            }

            List<Map<String, String>> results = new ArrayList<>();
            for (JsonNode article : articles) {
                String title = article.path("title").asText("");
                String description = article.path("description").asText("");
                String url = article.path("url").asText("");
                String sourceName = article.path("source").path("name").asText("");
                String publishedAt = article.path("publishedAt").asText("");
                if (!title.isBlank()) {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("title", title);
                    entry.put("description", description);
                    entry.put("url", url);
                    entry.put("sourceName", sourceName);
                    entry.put("publishedAt", publishedAt);
                    results.add(entry);
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("Failed to parse News API response", e);
            return List.of();
        }
    }

    public String buildSourceContext(List<Map<String, String>> sourceArticles) {
        if (sourceArticles.isEmpty()) {
            return "- No external source articles were available for this topic.";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sourceArticles.size(); i++) {
            Map<String, String> article = sourceArticles.get(i);
            sb.append(i + 1).append(". ")
                    .append("Title: ").append(article.getOrDefault("title", "")).append("\n")
                    .append("   Source: ").append(article.getOrDefault("sourceName", "")).append("\n")
                    .append("   URL: ").append(article.getOrDefault("url", "")).append("\n")
                    .append("   Published: ").append(article.getOrDefault("publishedAt", "")).append("\n")
                    .append("   Description: ").append(article.getOrDefault("description", "")).append("\n");
        }
        return sb.toString();
    }
}
