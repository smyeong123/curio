package com.curio.news.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Fetches source pages for the summary pipeline: the readable text of an article (so the
 * AI summarizes what the article says, not a one-line feed blurb) and a reachability check
 * for URLs the AI found itself through web search.
 *
 * <p>Every URL here is third-party input (feeds, NewsAPI, or AI output that a prompt-injected
 * page could steer), so each request hop is SSRF-checked: http(s) only, and the host must
 * resolve to public addresses — loopback, private, link-local (cloud metadata), CGNAT,
 * multicast and IPv6 ULA are refused. Redirects are followed by hand so every hop is checked.
 * Bodies are capped, and results are memoized per URL because the same article shows up
 * under several topics and both editions.
 */
@Component
@Slf4j
public class ArticleTextFetcher {

    /** Characters of article text handed to the prompt per article. */
    static final int MAX_TEXT_CHARS = 2000;
    /** Below this the page is treated as having no readable text (e.g. a JS-rendered shell). */
    static final int MIN_TEXT_CHARS = 200;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 4;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration BATCH_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final int MAX_CACHE_ENTRIES = 2000;
    private static final String USER_AGENT = "curio-news/1.0 (+https://curio-news.dev)";

    private record Cached(String text, long fetchedAtNanos) { }
    private record Page(int status, byte[] body, String finalUrl) { }

    private final HttpClient client;
    private final ExecutorService pool = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "article-fetch");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentHashMap<String, Cached> textCache = new ConcurrentHashMap<>();

    public ArticleTextFetcher() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    ArticleTextFetcher(HttpClient client) {
        this.client = client;
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }

    /**
     * Returns copies of {@code articles} with a {@code content} entry (readable article text)
     * on each one whose page could be fetched, fetched in parallel within an overall time
     * budget. Articles that can't be fetched (bot walls, JS-only pages, timeouts) keep just
     * their feed description. The inputs are never mutated — feed items are shared, cached maps.
     */
    public List<Map<String, String>> enrich(List<Map<String, String>> articles) {
        List<CompletableFuture<Optional<String>>> futures = new ArrayList<>();
        for (Map<String, String> article : articles) {
            String url = article.getOrDefault("url", "");
            futures.add(url.isBlank()
                    ? CompletableFuture.completedFuture(Optional.empty())
                    : CompletableFuture.supplyAsync(() -> fetchText(url), pool));
        }
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(BATCH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // Partial enrichment is fine: unfinished articles fall back to their description.
            log.info("Article enrichment did not finish for every article: {}", e.toString());
        }
        List<Map<String, String>> out = new ArrayList<>(articles.size());
        for (int i = 0; i < articles.size(); i++) {
            Map<String, String> copy = new java.util.LinkedHashMap<>(articles.get(i));
            Optional<String> text = futures.get(i).getNow(Optional.empty());
            text.ifPresent(t -> copy.put("content", t));
            out.add(copy);
        }
        return out;
    }

    /** The readable text of the page at {@code url}, or empty if it can't be fetched or has none. */
    public Optional<String> fetchText(String url) {
        Cached cached = textCache.get(url);
        if (cached != null && System.nanoTime() - cached.fetchedAtNanos() < CACHE_TTL.toNanos()) {
            return cached.text().isEmpty() ? Optional.empty() : Optional.of(cached.text());
        }
        String text = "";
        try {
            Page page = get(url);
            if (page != null && page.status() / 100 == 2) {
                text = extractText(page.body(), page.finalUrl());
            }
        } catch (Exception e) {
            log.debug("Could not fetch article text from {}: {}", url, e.toString());
        }
        if (textCache.size() > MAX_CACHE_ENTRIES) {
            textCache.clear();
        }
        textCache.put(url, new Cached(text, System.nanoTime()));
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    /**
     * Whether {@code url} points at a real page. Built to catch invented URLs (404, unknown
     * host), not to demand a readable page: a site that answers 401/403/429 to bots still
     * has the page, so those count as reachable.
     */
    public boolean isReachable(String url) {
        try {
            Page page = get(url);
            if (page == null) return false;
            int s = page.status();
            return s / 100 == 2 || s == 401 || s == 403 || s == 429;
        } catch (Exception e) {
            log.debug("Source URL unreachable {}: {}", url, e.toString());
            return false;
        }
    }

    /** GET with SSRF checks on every hop; null when the URL is refused or redirects too often. */
    private Page get(String url) throws IOException, InterruptedException {
        URI uri = URI.create(url.trim());
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!isPublicHttpUrl(uri)) {
                log.info("Refusing non-public source URL host: {}", uri.getHost());
                return null;
            }
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status / 100 == 3) {
                response.body().close();
                Optional<String> location = response.headers().firstValue("Location");
                if (location.isEmpty()) return new Page(status, new byte[0], uri.toString());
                uri = uri.resolve(location.get().trim());
                continue;
            }
            try (InputStream in = response.body()) {
                byte[] body = status / 100 == 2 ? readBounded(in) : new byte[0];
                return new Page(status, body, uri.toString());
            }
        }
        return null;
    }

    private static byte[] readBounded(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            throw new IOException("Article body exceeds " + MAX_BODY_BYTES + " bytes");
        }
        return bytes;
    }

    /** Pulls the article body out of a page: drops chrome, keeps paragraphs and subheadings. */
    static String extractText(byte[] html, String baseUrl) throws IOException {
        Document doc = Jsoup.parse(new ByteArrayInputStream(html), null, baseUrl);
        doc.select("script, style, noscript, nav, header, footer, aside, form, svg, iframe, button").remove();
        Element root = doc.selectFirst("article");
        if (root == null) root = doc.selectFirst("main");
        if (root == null) root = doc.selectFirst("[role=main]");
        if (root == null) root = doc.body();
        if (root == null) return "";

        StringBuilder sb = new StringBuilder();
        for (Element el : root.select("h2, h3, p")) {
            String line = el.text().trim();
            boolean heading = !"p".equals(el.normalName());
            // Short <p>s are mostly bylines, captions and share prompts.
            if (line.isEmpty() || (!heading && line.length() < 40)) continue;
            if (sb.length() + line.length() + 1 > MAX_TEXT_CHARS) {
                int room = MAX_TEXT_CHARS - sb.length();
                if (room > 80) sb.append(line, 0, room - 1).append('…');
                break;
            }
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(line);
        }
        return sb.length() < MIN_TEXT_CHARS ? "" : sb.toString();
    }

    static boolean isPublicHttpUrl(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) return false;
        String host = uri.getHost();
        if (host == null || host.isBlank()) return false;
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (!isPublicAddress(address)) return false;
            }
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    static boolean isPublicAddress(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return false;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet6Address) {
            return (b[0] & 0xFE) != 0xFC; // fc00::/7 unique-local
        }
        int first = b[0] & 0xFF;
        int second = b[1] & 0xFF;
        if (first == 0) return false;                              // 0.0.0.0/8
        if (first == 100 && second >= 64 && second <= 127) return false; // 100.64.0.0/10 CGNAT
        return true;
    }
}
