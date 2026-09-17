package com.curio.news.service;

import com.curio.shared.concurrent.SingleFlight;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API. The only provider that can search the web itself, so
 * when NewsAPI has no articles for a topic the shared summary prompt asks it to
 * (see {@link AbstractAiProvider#supportsWebSearch()}); everything else —
 * prompts, caching, BYOK routing, retries — is the shared orchestration.
 */
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "claude", matchIfMissing = true)
@Slf4j
public class ClaudeService extends AbstractAiProvider {

    @Value("${claude.api-key}")
    private String apiKey;

    @Value("${claude.model}")
    private String model;

    @Value("${claude.api-url}")
    private String apiUrl;

    public ClaudeService(ObjectMapper objectMapper,
                         RedisTemplate<String, Object> redisTemplate,
                         RestTemplateBuilder restTemplateBuilder,
                         NewsApiClient newsApiClient,
                         SingleFlight singleFlight,
                         CircuitBreakerRegistry circuitBreakerRegistry,
                         BulkheadRegistry bulkheadRegistry) {
        super(objectMapper, redisTemplate, restTemplateBuilder, newsApiClient, singleFlight,
                circuitBreakerRegistry, bulkheadRegistry);
    }

    @Override
    protected String providerName() {
        return "Claude";
    }

    @Override
    protected Duration readTimeout() {
        // Web search responses are slow; Claude needs a longer read timeout than the others.
        return Duration.ofSeconds(120);
    }

    @Override
    protected boolean supportsWebSearch() {
        return true;
    }

    /**
     * @param overrideApiKey if non-blank, used in place of the platform key
     *                       (BYOK). Charged to the caller's Anthropic account.
     */
    @Override
    protected String callApiRaw(String prompt, String overrideApiKey, boolean webSearch) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String keyInUse = (overrideApiKey != null && !overrideApiKey.isBlank()) ? overrideApiKey : apiKey;
        headers.set("x-api-key", keyInUse);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 4096);
        requestBody.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));

        if (webSearch) {
            Map<String, Object> webSearchTool = new LinkedHashMap<>();
            webSearchTool.put("type", "web_search_20250305");
            webSearchTool.put("name", "web_search");
            webSearchTool.put("max_uses", 5);
            requestBody.put("tools", List.of(webSearchTool));
        }

        return executeWithRetry(apiUrl, new HttpEntity<>(requestBody, headers));
    }

    @Override
    protected String extractResponseText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        JsonNode contentArray = root.path("content");
        if (contentArray.isMissingNode() || !contentArray.isArray() || contentArray.isEmpty()) {
            throw new RuntimeException("Claude API response missing 'content' array");
        }

        // With web search the response has several blocks (text, server_tool_use,
        // web_search_tool_result, text); the last non-empty text block is the answer.
        String text = null;
        for (JsonNode block : contentArray) {
            if ("text".equals(block.path("type").asText())) {
                String blockText = block.path("text").asText("").strip();
                if (!blockText.isEmpty()) {
                    text = blockText;
                }
            }
        }

        if (text == null) {
            throw new RuntimeException("Claude API response contains no text blocks");
        }

        return text;
    }
}
