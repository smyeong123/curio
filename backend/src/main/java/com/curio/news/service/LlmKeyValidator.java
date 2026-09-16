package com.curio.news.service;

import com.curio.user.entity.UserApiKey;
import com.curio.user.port.in.ApiKeyValidator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * Live "ping" against each LLM provider to confirm a user-supplied key is
 * authentic. Each implementation makes the smallest possible billable call
 * (or none at all where the provider exposes an auth-only endpoint) and
 * inspects the HTTP status:
 *   - 2xx → valid
 *   - 401/403 → invalid
 *   - 429/5xx → "couldn't tell" — we treat as invalid for safety
 *
 * The plaintext key never leaves this method's scope. We do NOT log the
 * key, the request body, or the response body.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LlmKeyValidator implements ApiKeyValidator {

    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${claude.api-url:https://api.anthropic.com/v1/messages}")
    private String claudeUrl;

    @Value("${claude.model:claude-sonnet-4-6}")
    private String claudeModel;

    @Value("${gemini.api-url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent}")
    private String geminiUrl;

    @Value("${openai.api-url:https://api.openai.com/v1/chat/completions}")
    private String openaiUrl;

    @Value("${openai.model:gpt-4o-mini}")
    private String openaiModel;

    private RestTemplate restTemplate;

    @PostConstruct
    void init() {
        // Validation is interactive — keep the timeout tight so a bad key
        // doesn't lock the UI for 30s. 8s is enough headroom for trans-pacific
        // round trips and a one-token completion.
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(8))
                .build();
    }

    @Override
    public boolean isValid(UserApiKey.Provider provider, String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) return false;
        try {
            return switch (provider) {
                case CLAUDE -> validateClaude(rawApiKey);
                case GEMINI -> validateGemini(rawApiKey);
                case OPENAI -> validateOpenAi(rawApiKey);
            };
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            // Expected: invalid key.
            return false;
        } catch (Exception e) {
            // Network blip / 429 / 5xx → we can't be sure, fail safe.
            log.warn("Key validation for {} could not complete: {}", provider, e.getClass().getSimpleName());
            return false;
        }
    }

    // ── Provider-specific pings ──────────────────────────────────────────

    private boolean validateClaude(String key) {
        // Single-token completion. Anthropic doesn't expose a free auth-only
        // endpoint; this costs ~0.0001 cent.
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("x-api-key", key);
        h.set("anthropic-version", "2023-06-01");
        Map<String, Object> body = Map.of(
                "model", claudeModel,
                "max_tokens", 1,
                "messages", java.util.List.of(Map.of("role", "user", "content", "ping"))
        );
        return restTemplate.exchange(claudeUrl, HttpMethod.POST,
                new HttpEntity<>(body, h), String.class).getStatusCode().is2xxSuccessful();
    }

    private boolean validateGemini(String key) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        // Header, not URL query string — keys in URLs leak into access logs/proxies.
        h.set("x-goog-api-key", key);
        Map<String, Object> body = Map.of(
                "contents", java.util.List.of(Map.of(
                        "parts", java.util.List.of(Map.of("text", "ping"))
                )),
                "generationConfig", Map.of("maxOutputTokens", 1)
        );
        return restTemplate.exchange(geminiUrl, HttpMethod.POST,
                new HttpEntity<>(body, h), String.class).getStatusCode().is2xxSuccessful();
    }

    private boolean validateOpenAi(String key) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(key);
        Map<String, Object> body = Map.of(
                "model", openaiModel,
                "max_tokens", 1,
                "messages", java.util.List.of(Map.of("role", "user", "content", "ping"))
        );
        return restTemplate.exchange(openaiUrl, HttpMethod.POST,
                new HttpEntity<>(body, h), String.class).getStatusCode().is2xxSuccessful();
    }
}
