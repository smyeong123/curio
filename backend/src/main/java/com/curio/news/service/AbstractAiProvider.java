package com.curio.news.service;

import com.curio.news.dto.NewsSummary;
import com.curio.news.dto.QuizGenerationResult;
import com.curio.shared.concurrent.SingleFlight;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Shared orchestration for the HTTP-based {@link AiService} providers (Claude, Gemini, OpenAI).
 * Holds the pieces that were byte-identical across all three implementations — the injected
 * collaborators, cache read/write, the retry/backoff loop, the circuit-breaker/bulkhead wrapper,
 * and the markdown-fence + JSON extraction tail — plus the summary/quiz orchestration that Gemini
 * and OpenAI share verbatim (Claude overrides those with its web-search + difficulty-aware variants).
 *
 * <p>Provider-specific wire format lives in the subclass hooks: {@link #providerName()},
 * {@link #callApiRaw(String, String)} (auth headers + request body + the network call) and
 * {@link #extractResponseText(JsonNode)} (response-shape navigation). The read timeout is
 * overridable via {@link #readTimeout()} (Claude uses a longer one for web search).
 */
@Slf4j
public abstract class AbstractAiProvider implements AiService {

    protected final ObjectMapper objectMapper;
    protected final RedisTemplate<String, Object> redisTemplate;
    protected final RestTemplateBuilder restTemplateBuilder;
    protected final NewsApiClient newsApiClient;
    protected final SingleFlight singleFlight;
    protected final CircuitBreakerRegistry circuitBreakerRegistry;
    protected final BulkheadRegistry bulkheadRegistry;

    protected RestTemplate restTemplate;

    protected static final int MAX_RETRIES = 2;
    protected static final long RETRY_DELAY_MS = 2000;

    // Lombok's @RequiredArgsConstructor can't chain to super, so the shared collaborators
    // are wired through this explicit constructor; subclasses call super(...).
    protected AbstractAiProvider(ObjectMapper objectMapper,
                                 RedisTemplate<String, Object> redisTemplate,
                                 RestTemplateBuilder restTemplateBuilder,
                                 NewsApiClient newsApiClient,
                                 SingleFlight singleFlight,
                                 CircuitBreakerRegistry circuitBreakerRegistry,
                                 BulkheadRegistry bulkheadRegistry) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.restTemplateBuilder = restTemplateBuilder;
        this.newsApiClient = newsApiClient;
        this.singleFlight = singleFlight;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
    }

    @PostConstruct
    void init() {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(readTimeout())
                .build();
    }

    /** Read timeout for the provider's HTTP client. Claude overrides it (web search is slow). */
    protected Duration readTimeout() {
        return Duration.ofSeconds(60);
    }

    // --- provider-specific hooks ---

    /** Human-readable provider name used in log messages and error text (e.g. "Claude"). */
    protected abstract String providerName();

    /**
     * Builds the provider's auth headers + request body for {@code prompt} and performs the
     * network call (through {@link #executeWithRetry(String, HttpEntity)}). When
     * {@code overrideApiKey} is non-blank it is used in place of the platform key (BYOK).
     */
    protected abstract String callApiRaw(String prompt, String overrideApiKey);

    /**
     * Parses the provider's response body and navigates to the model's raw (stripped) text output.
     * Receives the raw body so provider error messages can quote it verbatim; any parse/shape
     * failure is caught and rewrapped by {@link #extractTextFromResponse(String)}.
     */
    protected abstract String extractResponseText(String responseBody) throws Exception;

    // --- shared summary orchestration (Gemini/OpenAI; Claude overrides) ---

    @Override
    public List<NewsSummary> generateNewsSummaries(String topic) {
        String cacheKey = "news:summaries:" + topic + ":" + java.time.LocalDate.now();
        // Fast path: a warm cache serves without taking the single-flight lock.
        List<NewsSummary> cached = readCachedSummaries(cacheKey, topic);
        if (cached != null) {
            return cached;
        }
        // Cold cache: when the digest job fans many users across the executor, only the
        // first generates the (uncached) topic; the rest wait and read the cache the
        // winner just wrote (re-checked under the lock), instead of each firing an API call.
        return singleFlight.call(cacheKey, () -> generateNewsSummariesInternal(topic, null));
    }

    /** Reads + deserializes cached summaries for the key, or null on miss/garbage. */
    protected List<NewsSummary> readCachedSummaries(String cacheKey, String topic) {
        Object cached;
        try {
            cached = redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            // The cache is an optimization, never a dependency: a Redis outage must
            // degrade to a cache miss (regenerate), not zero out the daily run.
            log.warn("Redis cache read failed for topic {} — treating as miss: {}", topic, e.toString());
            return null;
        }
        if (cached != null) {
            try {
                return objectMapper.convertValue(cached, new TypeReference<List<NewsSummary>>() {});
            } catch (Exception e) {
                log.warn("Failed to deserialize cached summaries for topic {}", topic, e);
            }
        }
        return null;
    }

    /**
     * BYOK overload: bill the call to the user's key. Bypasses the shared Redis
     * cache (a user's key must not populate or read platform-cached content) and
     * the circuit breaker, mirroring ClaudeService's BYOK path.
     */
    @Override
    public List<NewsSummary> generateNewsSummaries(String topic, String overrideApiKey) {
        if (overrideApiKey == null || overrideApiKey.isBlank()) {
            return generateNewsSummaries(topic);
        }
        return generateNewsSummariesInternal(topic, overrideApiKey);
    }

    protected List<NewsSummary> generateNewsSummariesInternal(String topic, String overrideApiKey) {
        boolean useCache = (overrideApiKey == null || overrideApiKey.isBlank());
        String cacheKey = "news:summaries:" + topic + ":" + java.time.LocalDate.now();
        if (useCache) {
            // Re-check under the single-flight lock so waiters return the winner's result.
            List<NewsSummary> cached = readCachedSummaries(cacheKey, topic);
            if (cached != null) {
                return cached;
            }
        }

        List<Map<String, String>> sourceArticles = newsApiClient.fetchNewsArticles(topic);
        boolean hasSourceArticles = !sourceArticles.isEmpty();
        String sourceContext = newsApiClient.buildSourceContext(sourceArticles);

        String topicInstruction = "New & Emerging Models".equals(topic)
                ? "Scan for brand-new frontier or agentic model launches in the last 7 days. Prioritize models from labs outside the big five (Anthropic, OpenAI, DeepMind, xAI, Meta) and any new agentic systems. Avoid repeating widely-covered models."
                : "Focus on the most recent news, features, and releases for this topic. Prefer changes that happened in the last 14 days.";

        // With no fetched articles, never invent stories: instruct the model to only
        // report verifiable sources (or return []), rather than hallucinating "news"
        // with fabricated source URLs from its training data.
        String sourceInstruction = hasSourceArticles
                ? "Use the source articles below as your primary evidence. Prefer first-party announcements (lab blogs) over commentary."
                : "Only produce summaries you can attribute to a real, verifiable source with a valid source_url. If you cannot verify any current story for this topic, return an empty JSON array [].";
        String sourceContextBlock = hasSourceArticles ? "Source articles:\n" + sourceContext : "";

        String prompt = String.format("""
            You are Curio, a curator of AI *model* news. Your readers track new
            features, model releases, pricing changes, and brand-new agentic systems.
            Generate 2-3 plain-language TL;DR summaries for the topic: %s.

            %s

            %s

            %s

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

            Output format (JSON array):
            [
              {
                "headline": "...",
                "summary": "...",
                "why_it_matters": "...",
                "source_url": "https://example.com",
                "source_name": "Source Name",
                "topic": "%s"
              }
            ]

            Return ONLY the JSON array, no additional text.
            """, topic, topicInstruction, sourceInstruction, sourceContextBlock, topic);

        try {
            String response = callApi(prompt, overrideApiKey);
            List<NewsSummary> summaries = objectMapper.readValue(response, new TypeReference<List<NewsSummary>>() {});
            if (useCache && summaries != null && !summaries.isEmpty()) {
                try {
                    redisTemplate.opsForValue().set(cacheKey, summaries, Duration.ofHours(12));
                } catch (Exception e) {
                    // Cache write failure must not discard freshly generated summaries.
                    log.warn("Redis cache write failed for key {} — continuing uncached: {}", cacheKey, e.toString());
                }
            }
            return summaries != null ? summaries : List.of();
        } catch (CallNotPermittedException | BulkheadFullException e) {
            log.warn("{} call rejected by circuit breaker/bulkhead for topic {}: {}", providerName(), topic, e.toString());
            return List.of();
        } catch (Exception e) {
            log.error("Failed to parse {} API response for summaries", providerName(), e);
            return List.of();
        }
    }

    // --- shared quiz orchestration (Gemini/OpenAI; Claude overrides) ---

    @Override
    public QuizGenerationResult generateQuizQuestions(String digestContent) {
        return generateQuizQuestionsInternal(digestContent, null);
    }

    @Override
    public QuizGenerationResult generateQuizQuestions(String digestContent, String overrideApiKey) {
        if (overrideApiKey == null || overrideApiKey.isBlank()) {
            return generateQuizQuestions(digestContent);
        }
        return generateQuizQuestionsInternal(digestContent, overrideApiKey);
    }

    @Override
    public QuizGenerationResult generateQuizQuestions(String digestContent, DifficultyHint hint, String overrideApiKey) {
        // This provider's quiz prompt doesn't use the difficulty hint; just thread the BYOK
        // key so a user with their own key isn't billed to the platform key. Null key
        // routes to the platform path, preserving its circuit breaker.
        return generateQuizQuestions(digestContent, overrideApiKey);
    }

    protected QuizGenerationResult generateQuizQuestionsInternal(String digestContent, String overrideApiKey) {
        String prompt = String.format("""
            You are Curio's quiz generator. Based on this news digest, generate 5 multiple-choice questions to test reader comprehension.

            Digest content:
            %s

            Requirements:
            - Test factual recall and comprehension
            - 4 options per question (A, B, C, D)
            - Exactly one correct answer per question
            - Mix difficulty: 2 easy, 2 medium, 1 challenging
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
            """, digestContent);

        try {
            String response = callApi(prompt, overrideApiKey);
            return objectMapper.readValue(response, QuizGenerationResult.class);
        } catch (CallNotPermittedException | BulkheadFullException e) {
            log.warn("{} call rejected by circuit breaker/bulkhead for quiz generation: {}", providerName(), e.toString());
            return QuizGenerationResult.builder().questions(List.of()).build();
        } catch (Exception e) {
            log.error("Failed to generate quiz questions via {}", providerName(), e);
            return QuizGenerationResult.builder().questions(List.of()).build();
        }
    }

    // --- shared API call plumbing (used by all three providers) ---

    /**
     * Routes platform-key calls (null/blank override) through the shared aiProvider
     * circuit breaker and bulkhead; BYOK calls go straight to the raw call so a flaky
     * user key can't trip the shared breaker. Programmatic rather than annotation-based:
     * the resilience4j annotations are proxy-based and were silently skipped on the
     * self-invoked overload chains, and annotating the outer summary method made waiters
     * hold a bulkhead permit for the whole SingleFlight lock wait.
     */
    protected String callApi(String prompt, String overrideApiKey) {
        if (overrideApiKey != null && !overrideApiKey.isBlank()) {
            return callApiRaw(prompt, overrideApiKey);
        }
        return runProtected(() -> callApiRaw(prompt, null));
    }

    /** Runs a platform-key supplier under the shared aiProvider circuit breaker + bulkhead. */
    protected String runProtected(Supplier<String> rawCall) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("aiProvider");
        Bulkhead bulkhead = bulkheadRegistry.bulkhead("aiProvider");
        return circuitBreaker.executeSupplier(Bulkhead.decorateSupplier(bulkhead, rawCall));
    }

    /**
     * Shared retry/backoff loop: POSTs {@code entity} to {@code url}, extracting the model
     * text on success. 429 is retried honoring {@code Retry-After}; other 4xx fail fast; 5xx
     * and transport errors are retried with exponential backoff up to {@link #MAX_RETRIES}.
     */
    protected String executeWithRetry(String url, HttpEntity<Map<String, Object>> entity) {
        Exception lastException = null;
        long retryAfterMs = 0;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = Math.max(RETRY_DELAY_MS * attempt, retryAfterMs);
                    log.info("Retrying {} API call (attempt {}/{}) after {}ms", providerName(), attempt + 1, MAX_RETRIES + 1, delay);
                    Thread.sleep(delay);
                }

                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.POST, entity, String.class);

                return extractTextFromResponse(response.getBody());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(providerName() + " API retry interrupted", ie);
            } catch (HttpClientErrorException e) {
                // 4xx other than 429 will never succeed on retry (bad request, invalid
                // or revoked key, forbidden) — fail fast instead of burning backoff and
                // the shared circuit-breaker budget. 429 falls through to retry.
                if (e.getStatusCode().value() != 429) {
                    throw new RuntimeException(providerName() + " API client error: " + e.getStatusCode(), e);
                }
                lastException = e;
                retryAfterMs = parseRetryAfterMs(e);
                log.warn("{} API call attempt {} rate-limited (429): {}", providerName(), attempt + 1, e.getMessage());
            } catch (Exception e) {
                // 5xx, timeouts and other transport errors — retryable.
                lastException = e;
                retryAfterMs = (e instanceof HttpStatusCodeException hsce) ? parseRetryAfterMs(hsce) : 0;
                log.warn("{} API call attempt {} failed: {}", providerName(), attempt + 1, e.getMessage());
            }
        }

        log.error("{} API call failed after {} attempts", providerName(), MAX_RETRIES + 1, lastException);
        throw new RuntimeException("Failed to call " + providerName() + " API after retries", lastException);
    }

    /** Parses the provider's {@code Retry-After} header (delta-seconds) into ms, or 0 if absent/unparseable. */
    protected long parseRetryAfterMs(HttpStatusCodeException e) {
        HttpHeaders responseHeaders = e.getResponseHeaders();
        if (responseHeaders == null) {
            return 0;
        }
        String retryAfter = responseHeaders.getFirst("Retry-After");
        if (retryAfter == null || retryAfter.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(retryAfter.trim()) * 1000;
        } catch (NumberFormatException nfe) {
            // HTTP-date form is not handled; fall back to the fixed backoff.
            return 0;
        }
    }

    // --- shared response parsing tail ---

    /** Max characters of a provider response body to keep in logs/exceptions. */
    private static final int MAX_LOGGED_BODY_CHARS = 500;

    /**
     * Trims a provider response body to a bounded prefix before it enters a log
     * line or exception message. The body is the provider's response (never the
     * request), so it carries no API key, but it can be large and, on a safety/
     * content-filter block, may echo user content — keep only a short diagnostic
     * prefix rather than the whole payload.
     */
    protected static String truncateForLog(String body) {
        if (body == null) return "";
        String trimmed = body.strip();
        if (trimmed.length() <= MAX_LOGGED_BODY_CHARS) return trimmed;
        return trimmed.substring(0, MAX_LOGGED_BODY_CHARS) + "…(" + trimmed.length() + " chars total)";
    }

    /** Parses the response envelope (via {@link #extractResponseText(String)}) down to bare JSON. */
    protected String extractTextFromResponse(String responseBody) {
        try {
            String text = extractResponseText(responseBody);
            return stripToJson(text);
        } catch (Exception e) {
            log.error("Failed to extract text from {} response: {}", providerName(), truncateForLog(responseBody), e);
            throw new RuntimeException("Failed to parse " + providerName() + " API response", e);
        }
    }

    /**
     * Strips markdown code fences and any leading prose so what remains starts at the JSON.
     * Expects an already-stripped model text (as returned by {@link #extractResponseText(JsonNode)}).
     */
    protected String stripToJson(String text) {
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }

        text = text.strip();

        // Recover from any leading prose before the JSON (e.g. "Here are the summaries:\n[...]").
        int jsonStart = firstJsonStart(text);
        if (jsonStart > 0) {
            text = text.substring(jsonStart);
        }

        return text.strip();
    }

    /** Index of the first '[' or '{' (where the JSON starts), or -1 if neither is present. */
    protected static int firstJsonStart(String text) {
        int arrayStart = text.indexOf('[');
        int objectStart = text.indexOf('{');
        if (arrayStart >= 0 && objectStart >= 0) {
            return Math.min(arrayStart, objectStart);
        }
        return Math.max(arrayStart, objectStart);
    }

}
