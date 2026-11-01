package com.curio.news.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds the stories people who follow AI are actually discussing: recent Hacker News
 * stories whose title matches a topic's phrases (see {@link TopicQueries#HACKER_NEWS})
 * and that cleared a points bar. Uses the public Algolia HN search API (no key).
 *
 * <p>Each article links to the story's own URL (the original page), not the HN thread;
 * the points and comment count go in the description so the model can weigh importance.
 * A failed search is logged and skipped, like a dead feed.
 */
@Component
@Slf4j
public class HackerNewsFetcher {

    static final String SEARCH_URL = "https://hn.algolia.com/api/v1/search";
    /** Below this a story hasn't drawn enough attention to count as news. */
    static final int MIN_POINTS = 60;
    static final int MAX_STORIES_PER_TOPIC = 5;
    private static final int HITS_PER_PHRASE = 8;
    private static final Duration LOOKBACK = Duration.ofDays(14);
    private static final Duration CACHE_TTL = Duration.ofMinutes(45);

    private record Cached(List<Map<String, String>> items, long fetchedAtNanos) { }

    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();
    private RestTemplate restTemplate;
    Clock clock = Clock.systemUTC();

    public HackerNewsFetcher(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.restTemplateBuilder = restTemplateBuilder;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** The topic's most-discussed recent stories, highest points first. */
    public List<Map<String, String>> fetchForTopic(String topic) {
        List<Map<String, String>> all = new ArrayList<>();
        for (String phrase : TopicQueries.hackerNewsPhrases(topic)) {
            all.addAll(search(phrase));
        }
        Set<String> seen = new HashSet<>();
        return all.stream()
                .sorted(Comparator.comparingInt((Map<String, String> m) -> Integer.parseInt(m.get("points"))).reversed())
                .filter(m -> seen.add(NewsApiClient.normalizeUrl(m.get("url"))))
                .limit(MAX_STORIES_PER_TOPIC)
                .toList();
    }

    private List<Map<String, String>> search(String phrase) {
        Cached cached = cache.get(phrase);
        if (cached != null && System.nanoTime() - cached.fetchedAtNanos() < CACHE_TTL.toNanos()) {
            return cached.items();
        }
        try {
            long since = clock.instant().minus(LOOKBACK).getEpochSecond();
            URI uri = UriComponentsBuilder.fromHttpUrl(SEARCH_URL)
                    .queryParam("query", "{query}")
                    .queryParam("tags", "story")
                    .queryParam("numericFilters", "{filters}")
                    .queryParam("restrictSearchableAttributes", "title")
                    .queryParam("advancedSyntax", "true")
                    .queryParam("hitsPerPage", HITS_PER_PHRASE)
                    .encode()
                    .buildAndExpand("\"" + phrase + "\"", "points>=" + MIN_POINTS + ",created_at_i>" + since)
                    .toUri();
            String body = restTemplate.getForObject(uri, String.class);
            List<Map<String, String>> items = parse(body);
            cache.put(phrase, new Cached(items, System.nanoTime()));
            return items;
        } catch (Exception e) {
            log.warn("Hacker News search failed for '{}': {}", phrase, e.toString());
            return List.of();
        }
    }

    /** Stories with an external http(s) link; Ask/Show HN posts without one are skipped. */
    List<Map<String, String>> parse(String body) throws Exception {
        if (body == null || body.isBlank()) return List.of();
        JsonNode hits = objectMapper.readTree(body).path("hits");
        List<Map<String, String>> out = new ArrayList<>();
        for (JsonNode hit : hits) {
            String url = hit.path("url").asText("");
            String title = hit.path("title").asText("");
            if (title.isBlank() || !(url.startsWith("https://") || url.startsWith("http://"))) continue;
            int points = hit.path("points").asInt(0);
            int comments = hit.path("num_comments").asInt(0);
            Map<String, String> item = new LinkedHashMap<>();
            item.put("title", title);
            item.put("description", "Discussed on Hacker News: " + points + " points, " + comments + " comments.");
            item.put("url", url);
            item.put("sourceName", hostOf(url));
            item.put("publishedAt", hit.path("created_at").asText(""));
            item.put("points", Integer.toString(points));
            out.add(item);
        }
        return out;
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host.replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
