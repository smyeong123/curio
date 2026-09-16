package com.curio.news.service;

import com.curio.news.dto.NewsSummary;
import com.curio.news.dto.QuizGenerationResult;
import com.curio.shared.concurrent.SingleFlight;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

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
    public List<NewsSummary> generateNewsSummaries(String topic) {
        String cacheKey = "news:summaries:" + topic + ":" + java.time.LocalDate.now();
        // Fast path: a warm cache serves without taking the single-flight lock.
        List<NewsSummary> cached = readCachedSummaries(cacheKey, topic);
        if (cached != null) {
            return cached;
        }
        // Cold cache: the digest job fans many users across the executor, so several
        // can reach this topic at once before anyone writes the cache. Serialize them
        // per topic+date so the topic is generated ONCE; waiters re-check the cache
        // under the lock and hit it instead of firing their own API call.
        return singleFlight.call(cacheKey, () -> {
            List<NewsSummary> recheck = readCachedSummaries(cacheKey, topic);
            return recheck != null ? recheck : generateAndCacheSummaries(topic, cacheKey);
        });
    }

    private List<NewsSummary> generateAndCacheSummaries(String topic, String cacheKey) {
        List<Map<String, String>> sourceArticles = newsApiClient.fetchNewsArticles(topic);
        boolean hasSourceArticles = !sourceArticles.isEmpty();
        String sourceContext = newsApiClient.buildSourceContext(sourceArticles);

        String prompt = """
            You are Curio, a curator of AI *model* news. Your readers track new
            features, model releases, pricing changes, and brand-new agentic systems.
            Generate 2-3 plain-language TL;DR summaries for the topic: {TOPIC}.

            {TOPIC_INSTRUCTION}

            {SOURCE_INSTRUCTION}

            {SOURCE_CONTEXT_BLOCK}

            Requirements for each summary:
            - Write for a curious NON-EXPERT who has no technical background
            - summary: a 50-75 word TL;DR — NEVER exceed 80 words — in short, everyday sentences (under 20 words each)
            - Lead with what actually happened and why an ordinary person would care
            - Explain or avoid jargon — if a technical term is unavoidable (e.g. "subagent",
              "API", "context window"), define it in plain words in the same sentence
            - Skip inside-baseball: no SDK/package renames, version-number minutiae, or billing
              mechanics unless it directly changes what everyday users can do
            - Merge near-duplicate stories into a single summary; never cover the same announcement twice
            - headline: plain and specific, no hype or clickbait
            - "why_it_matters": one concrete, plain-language sentence about the real-world impact
            - Prefer first-party announcements (lab blogs) over commentary
            - MUST include a real, valid source_url and source_name for each summary

            Output format (JSON array):
            [
              {
                "headline": "...",
                "summary": "...",
                "why_it_matters": "...",
                "source_url": "https://example.com/actual-article",
                "source_name": "Source Name",
                "topic": "{TOPIC}"
              }
            ]

            Return ONLY the JSON array, no additional text.
            """.replace("{TOPIC}", topic)
                .replace("{TOPIC_INSTRUCTION}",
                        "New & Emerging Models".equals(topic)
                                ? "Scan for brand-new frontier or agentic model launches in the last 7 days — including models you may not have heard of. Prioritize launches from labs outside the big five (Anthropic, OpenAI, DeepMind, xAI, Meta) and any new agentic systems. Avoid repeating models already widely covered."
                                : "Focus on the most recent news, features, and releases for this topic. Prefer changes that happened in the last 14 days.");

        if (hasSourceArticles) {
            prompt = prompt
                    .replace("{SOURCE_INSTRUCTION}", "Use the source articles below as your primary evidence.")
                    .replace("{SOURCE_CONTEXT_BLOCK}", "Source articles:\n" + sourceContext);
        } else {
            prompt = prompt
                    .replace("{SOURCE_INSTRUCTION}", "Search the web for the latest news on this topic. Use the search results to write summaries with real source URLs.")
                    .replace("{SOURCE_CONTEXT_BLOCK}", "");
        }

        List<NewsSummary> summaries;
        if (hasSourceArticles) {
            log.info("Generating summaries for topic '{}' with {} source articles", topic, sourceArticles.size());
            summaries = callClaudeApi(prompt);
        } else {
            log.info("Generating summaries for topic '{}' using web search (no source articles)", topic);
            summaries = callClaudeApiWithWebSearch(prompt);
        }

        log.info("Summaries result for topic '{}': {} items", topic, summaries != null ? summaries.size() : "null");

        if (summaries != null && !summaries.isEmpty()) {
            try {
                redisTemplate.opsForValue().set(cacheKey, summaries, Duration.ofHours(12));
            } catch (Exception e) {
                // Cache write failure must not discard freshly generated summaries.
                log.warn("Redis cache write failed for key {} — continuing uncached: {}", cacheKey, e.toString());
            }
        }

        return summaries != null ? summaries : List.of();
    }

    /**
     * BYOK overload — substitutes the user's Anthropic key for this call.
     * Skips the Redis cache because cached summaries from one user's key
     * shouldn't surface for another user's request, and skips the
     * platform-level circuit breaker for the same reason (a flaky user key
     * shouldn't trip the breaker for everyone else).
     */
    @Override
    public List<NewsSummary> generateNewsSummaries(String topic, String overrideApiKey) {
        if (overrideApiKey == null || overrideApiKey.isBlank()) {
            return generateNewsSummaries(topic);
        }
        List<Map<String, String>> sourceArticles = newsApiClient.fetchNewsArticles(topic);
        boolean hasSourceArticles = !sourceArticles.isEmpty();
        String sourceContext = newsApiClient.buildSourceContext(sourceArticles);

        String prompt = """
            You are Curio, a curator of AI *model* news. Generate 2-3 plain-language TL;DR summaries for: {TOPIC}.
            Write for a curious non-expert: each summary 50-75 words (never exceed 80) in short everyday sentences,
            explain or avoid jargon, skip SDK/version/billing minutiae, and merge duplicate stories.
            {TOPIC_INSTRUCTION}
            {SOURCE_INSTRUCTION}
            {SOURCE_CONTEXT_BLOCK}
            Output a JSON array of objects with keys: headline, summary (50-80 word plain-language TL;DR),
            why_it_matters, source_url, source_name, topic. Return only JSON.
            """.replace("{TOPIC}", topic).replace("{TOPIC_INSTRUCTION}", "");

        prompt = hasSourceArticles
                ? prompt.replace("{SOURCE_INSTRUCTION}", "Use the source articles below as primary evidence.")
                        .replace("{SOURCE_CONTEXT_BLOCK}", "Source articles:\n" + sourceContext)
                : prompt.replace("{SOURCE_INSTRUCTION}", "Search the web for the latest news on this topic.")
                        .replace("{SOURCE_CONTEXT_BLOCK}", "");

        try {
            String response = callClaudeApiRaw(prompt, !hasSourceArticles, overrideApiKey);
            return objectMapper.readValue(response, new TypeReference<List<NewsSummary>>() {});
        } catch (Exception e) {
            log.warn("BYOK Claude call failed for topic '{}': {}", topic, e.getMessage());
            return List.of();
        }
    }

    @Override
    public QuizGenerationResult generateQuizQuestions(String digestContent, String overrideApiKey) {
        return generateQuizQuestions(digestContent, DifficultyHint.NORMAL, overrideApiKey);
    }

    @Override
    public QuizGenerationResult generateQuizQuestions(String digestContent, DifficultyHint hint, String overrideApiKey) {
        if (overrideApiKey == null || overrideApiKey.isBlank()) {
            return generateQuizQuestions(digestContent, hint);
        }
        // BYOK: bill the quiz to the user's key and skip the platform circuit breaker,
        // mirroring the summaries BYOK path. Still honours the difficulty hint and the
        // full comprehension prompt (the old BYOK path used a bare-bones prompt).
        return generateQuizQuestionsInternal(digestContent, hint, overrideApiKey);
    }

    @Override
    public QuizGenerationResult generateQuizQuestions(String digestContent, DifficultyHint hint) {
        return generateQuizQuestionsInternal(digestContent, hint, null);
    }

    @Override
    public QuizGenerationResult generateQuizQuestions(String digestContent) {
        return generateQuizQuestionsInternal(digestContent, DifficultyHint.NORMAL, null);
    }

    private QuizGenerationResult generateQuizQuestionsInternal(String digestContent, DifficultyHint hint, String overrideApiKey) {
        String difficultyMix = switch (hint == null ? DifficultyHint.NORMAL : hint) {
            case EASIER  -> "3 easy, 2 medium, 0 challenging";
            case HARDER  -> "0 easy, 2 medium, 3 challenging";
            case NORMAL  -> "2 easy, 2 medium, 1 challenging";
        };
        String prompt = String.format("""
            You are Curio's quiz generator. Based on this news digest, generate 5 multiple-choice questions to test reader comprehension.

            Digest content:
            %s

            Requirements:
            - Test factual recall and comprehension
            - 4 options per question (A, B, C, D)
            - Exactly one correct answer per question
            - Mix difficulty: %s
            - Questions should be clear and unambiguous

            Output format (JSON):
            {
              "questions": [
                {
                  "id": 1,
                  "question": "...",
                  "options": {
                    "A": "...",
                    "B": "...",
                    "C": "...",
                    "D": "..."
                  },
                  "correct": "A",
                  "explanation": "..."
                }
              ]
            }

            Return ONLY the JSON object, no additional text.
            """, digestContent, difficultyMix);

        try {
            String response = (overrideApiKey == null || overrideApiKey.isBlank())
                    ? callClaudeApiProtected(prompt, false)
                    : callClaudeApiRaw(prompt, false, overrideApiKey);
            return objectMapper.readValue(response, QuizGenerationResult.class);
        } catch (CallNotPermittedException | BulkheadFullException e) {
            log.warn("Claude call rejected by circuit breaker/bulkhead for quiz generation: {}", e.toString());
            return QuizGenerationResult.builder().questions(List.of()).build();
        } catch (Exception e) {
            log.error("Failed to generate quiz questions", e);
            return QuizGenerationResult.builder().questions(List.of()).build();
        }
    }

    private List<NewsSummary> callClaudeApi(String prompt) {
        try {
            String response = callClaudeApiProtected(prompt, false);
            return objectMapper.readValue(response, new TypeReference<List<NewsSummary>>() {});
        } catch (CallNotPermittedException | BulkheadFullException e) {
            log.warn("Claude call rejected by circuit breaker/bulkhead for news summaries: {}", e.toString());
            return List.of();
        } catch (Exception e) {
            log.error("Failed to parse Claude API response", e);
            return List.of();
        }
    }

    private List<NewsSummary> callClaudeApiWithWebSearch(String prompt) {
        try {
            String response = callClaudeApiProtected(prompt, true);
            log.debug("Web search raw response text (first 500 chars): {}", response != null ? response.substring(0, Math.min(500, response.length())) : "null");
            return objectMapper.readValue(response, new TypeReference<List<NewsSummary>>() {});
        } catch (CallNotPermittedException | BulkheadFullException e) {
            log.warn("Claude call rejected by circuit breaker/bulkhead for news summaries (web search): {}", e.toString());
            return List.of();
        } catch (Exception e) {
            log.error("Failed to parse Claude API web search response", e);
            return List.of();
        }
    }

    /**
     * Runs a platform-key call under the shared aiProvider circuit breaker and bulkhead.
     * Programmatic rather than annotation-based: the resilience4j annotations are
     * proxy-based, so they were silently skipped on the self-invoked overload chains
     * (the quiz path had no protection at all), and annotating the outer summary method
     * made waiters hold a bulkhead permit for the whole SingleFlight lock wait. Guarding
     * the raw network call keeps permits scoped to actual API time. BYOK calls bypass
     * this on purpose — a flaky user key must not trip the shared breaker.
     */
    private String callClaudeApiProtected(String prompt, boolean useWebSearch) {
        return runProtected(() -> callClaudeApiRaw(prompt, useWebSearch, null));
    }

    /**
     * The abstract single-call hook. Claude's own paths call {@link #callClaudeApiRaw}
     * directly (they need the web-search flag), so this delegates without web search.
     */
    @Override
    protected String callApiRaw(String prompt, String overrideApiKey) {
        return callClaudeApiRaw(prompt, false, overrideApiKey);
    }

    /**
     * @param overrideApiKey if non-blank, used in place of the platform key
     *                       (BYOK). Charged to the caller's Anthropic account.
     */
    private String callClaudeApiRaw(String prompt, boolean useWebSearch, String overrideApiKey) {
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

        if (useWebSearch) {
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

        // Find the last text block — with web search, the response has multiple blocks
        // (text, server_tool_use, web_search_tool_result, text) and the final text has the answer
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
