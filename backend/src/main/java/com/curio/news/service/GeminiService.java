package com.curio.news.service;

import com.curio.shared.concurrent.SingleFlight;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini")
public class GeminiService extends AbstractAiProvider {

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.api-url}")
    private String apiUrl;

    public GeminiService(ObjectMapper objectMapper,
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
        return "Gemini";
    }

    /** {@code webSearch} is ignored: no Gemini search tool is wired here (see {@link #supportsWebSearch()}). */
    @Override
    protected String callApiRaw(String prompt, String overrideApiKey, boolean webSearch) {
        String keyInUse = (overrideApiKey != null && !overrideApiKey.isBlank()) ? overrideApiKey : apiKey;
        // Pass the key via header, never the URL query string — keys in URLs leak
        // into access logs, proxies and RestTemplate request logging.
        String url = apiUrl;

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", keyInUse);

        return executeWithRetry(url, new HttpEntity<>(requestBody, headers));
    }

    @Override
    protected String extractResponseText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        // A safety block returns candidates absent/empty (often only promptFeedback);
        // guard before get(0) so this surfaces as a clear error instead of an NPE
        // silently collapsing to an empty digest.
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new RuntimeException("Gemini response has no candidates (possible safety block): " + truncateForLog(responseBody));
        }

        JsonNode text = candidates.get(0)
                .path("content").path("parts").get(0)
                .path("text");

        if (text.isMissingNode() || text.isNull()) {
            throw new RuntimeException("Gemini API response missing expected text field");
        }

        return text.asText().strip();
    }

}
