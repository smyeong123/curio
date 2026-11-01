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

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewsApiClient {

    private final ObjectMapper objectMapper;
    private final RestTemplateBuilder restTemplateBuilder;
    private final LabBlogFetcher labBlogFetcher;
    private final HackerNewsFetcher hackerNewsFetcher;
    private final ArticleTextFetcher articleTextFetcher;

    /** Articles older than this are not news any more; feeds keep years-old posts. */
    static final Duration MAX_ARTICLE_AGE = Duration.ofDays(14);
    /** Articles handed to one summary prompt (each may carry up to 2,000 chars of text). */
    static final int MAX_ARTICLES_PER_TOPIC = 10;

    /** Package-private so tests can pin "now" against fixed article dates. */
    Clock clock = Clock.systemUTC();

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

    /** At most this many lab-feed articles, so community and press coverage still get a say. */
    static final int MAX_LAB_ARTICLES = 6;
    /** At most this many Hacker News stories per topic. */
    static final int MAX_HN_ARTICLES = 3;

    /**
     * Fetches recent articles for a topic from three kinds of source: first-party lab and
     * product feeds (release notes), Hacker News stories people are discussing, and NewsAPI
     * press coverage. Stale and duplicate articles are dropped. Each source gets a share of
     * the {@link #MAX_ARTICLES_PER_TOPIC} slots (lab first, then Hacker News, then NewsAPI);
     * slots a source can't fill go to the others. Each article gets its readable page text
     * under {@code content} where the page could be fetched.
     *
     * <p>The returned order is the numbering the summary prompt uses: the AI cites an
     * article by its 1-based position, and the URL is taken from here, never from the AI.
     */
    public List<Map<String, String>> fetchNewsArticles(String topic) {
        Instant cutoff = clock.instant().minus(MAX_ARTICLE_AGE);
        Set<String> seenUrls = new HashSet<>();
        List<Map<String, String>> lab = fresh(labBlogFetcher.fetchForTopic(topic), cutoff, seenUrls);
        List<Map<String, String>> hn = fresh(hackerNewsFetcher.fetchForTopic(topic), cutoff, seenUrls);
        List<Map<String, String>> press = fresh(fetchFromNewsApi(topic), cutoff, seenUrls);

        List<Map<String, String>> selected = new ArrayList<>();
        selected.addAll(lab.subList(0, Math.min(lab.size(), MAX_LAB_ARTICLES)));
        selected.addAll(hn.subList(0, Math.min(hn.size(), MAX_HN_ARTICLES)));
        for (List<Map<String, String>> rest : List.of(press,
                lab.subList(Math.min(lab.size(), MAX_LAB_ARTICLES), lab.size()),
                hn.subList(Math.min(hn.size(), MAX_HN_ARTICLES), hn.size()))) {
            for (Map<String, String> article : rest) {
                if (selected.size() >= MAX_ARTICLES_PER_TOPIC) break;
                selected.add(article);
            }
        }
        if (selected.size() > MAX_ARTICLES_PER_TOPIC) {
            selected = new ArrayList<>(selected.subList(0, MAX_ARTICLES_PER_TOPIC));
        }
        return selected.isEmpty() ? List.of() : articleTextFetcher.enrich(selected);
    }

    /** The http(s) articles not older than {@code cutoff} whose URL isn't in {@code seenUrls} yet (undated ones are kept). */
    private static List<Map<String, String>> fresh(List<Map<String, String>> articles, Instant cutoff, Set<String> seenUrls) {
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, String> article : articles) {
            String url = article.getOrDefault("url", "").trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) continue;
            Instant published = parsePublishedAt(article.getOrDefault("publishedAt", ""));
            if (published != null && published.isBefore(cutoff)) continue;
            if (!seenUrls.add(normalizeUrl(url))) continue;
            out.add(article);
        }
        return out;
    }

    /** Whether a URL the AI found through web search points at a real page. */
    public boolean isReachable(String url) {
        return articleTextFetcher.isReachable(url);
    }

    /** Same article, different tracking params or trailing slash → one entry. */
    static String normalizeUrl(String url) {
        String u = url.trim().toLowerCase(Locale.ROOT);
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        int h = u.indexOf('#');
        if (h >= 0) u = u.substring(0, h);
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u.replaceFirst("^https?://(www\\.)?", "");
    }

    /**
     * Parses the date formats our sources use: RFC 1123 (RSS pubDate, with or without a
     * zone) and ISO-8601 (Atom, NewsAPI). Null when unparseable — those articles are kept.
     */
    static Instant parsePublishedAt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) { }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException ignored) { }
        try {
            return LocalDateTime.parse(value, RSS_DATE_NO_ZONE).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) { }
        return null;
    }

    private static final DateTimeFormatter RSS_DATE_NO_ZONE =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss", Locale.ENGLISH);

    private List<Map<String, String>> fetchFromNewsApi(String topic) {
        if (newsApiKey == null || newsApiKey.isBlank()) {
            return List.of();
        }

        try {
            // The query goes in as a template variable so its quotes, parentheses and any
            // "&" are percent-encoded; a literal "&" would otherwise split the parameter.
            java.net.URI url = UriComponentsBuilder.fromHttpUrl(newsApiUrl)
                    .queryParam("q", "{q}")
                    .queryParam("searchIn", "title,description")
                    .queryParam("language", "en")
                    .queryParam("sortBy", "publishedAt")
                    .queryParam("pageSize", 10)
                    .encode()
                    .buildAndExpand(TopicQueries.newsApiQuery(topic))
                    .toUri();

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
            sb.append("[").append(i + 1).append("] ")
                    .append("Title: ").append(article.getOrDefault("title", "")).append("\n")
                    .append("   Source: ").append(article.getOrDefault("sourceName", "")).append("\n")
                    .append("   Published: ").append(article.getOrDefault("publishedAt", "")).append("\n")
                    .append("   Description: ").append(article.getOrDefault("description", "")).append("\n");
            String content = article.getOrDefault("content", "");
            if (!content.isBlank()) {
                sb.append("   Article text:\n").append(content.indent(6));
            }
        }
        return sb.toString();
    }
}
