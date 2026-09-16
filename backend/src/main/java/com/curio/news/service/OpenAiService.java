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
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAiService extends AbstractAiProvider {

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.api-url}")
    private String apiUrl;

    @Value("${openai.model}")
    private String model;

    public OpenAiService(ObjectMapper objectMapper,
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
        return "OpenAI";
    }

    @Override
    protected String callApiRaw(String prompt, String overrideApiKey) {
        String keyInUse = (overrideApiKey != null && !overrideApiKey.isBlank()) ? overrideApiKey : apiKey;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + keyInUse);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        return executeWithRetry(apiUrl, new HttpEntity<>(requestBody, headers));
    }

    @Override
    protected String extractResponseText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        // A content filter can return choices absent/empty; guard before get(0) so
        // this surfaces as a clear error instead of an NPE silently collapsing to
        // an empty digest.
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new RuntimeException("OpenAI response has no choices (possible content filter): " + truncateForLog(responseBody));
        }

        JsonNode content = choices.get(0)
                .path("message").path("content");

        if (content.isMissingNode() || content.isNull()) {
            throw new RuntimeException("OpenAI API response missing expected content field");
        }

        return content.asText().strip();
    }

}
