package com.curio.news.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import jakarta.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches recent posts from first-party AI lab RSS/Atom feeds (Anthropic,
 * OpenAI, DeepMind, Meta AI, Mistral, Hugging Face). Returns the same
 * {@code List<Map<String, String>>} shape as {@link NewsApiClient} so
 * downstream callers can treat the two sources identically.
 *
 * Intentionally dep-free — uses the JDK XML parser. RSS 2.0 uses
 * {@code <item>} elements with {@code <title>/<link>/<description>/<pubDate>};
 * Atom 1.0 uses {@code <entry>} with {@code <title>/<link href>/<summary>/<updated>}.
 * Both shapes are handled.
 */
@Component
@Slf4j
public class LabBlogFetcher {

    private static final int MAX_ITEMS_PER_FEED = 5;

    /**
     * Hard cap on a feed response body. Feeds are third-party input fetched on
     * every digest run; without a ceiling a misbehaving feed (or a redirect to a
     * huge asset) buffers arbitrarily much into heap and one OOM kills the whole
     * run. 2 MB comfortably fits every real RSS/Atom feed we consume.
     */
    private static final int MAX_FEED_BYTES = 2 * 1024 * 1024;

    private final RestTemplateBuilder restTemplateBuilder;
    private final MeterRegistry meterRegistry;

    private RestTemplate restTemplate;
    private DocumentBuilderFactory documentBuilderFactory;

    /**
     * A feed is fetched once per {@link #FEED_TTL} per instance, not once per
     * (topic, edition) cache miss and per BYOK user: the Hugging Face feed alone
     * sits on every topic, so without this memo a cold day downloads it dozens of
     * times. Errors are not memoized, so a flaky feed is retried on the next call.
     */
    static final Duration FEED_TTL = Duration.ofMinutes(45);
    private record CachedFeed(List<Map<String, String>> items, long fetchedAtNanos) { }
    private final java.util.concurrent.ConcurrentHashMap<String, CachedFeed> feedCache = new java.util.concurrent.ConcurrentHashMap<>();

    public LabBlogFetcher(RestTemplateBuilder restTemplateBuilder,
                          ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.restTemplateBuilder = restTemplateBuilder;
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
    }

    @PostConstruct
    void init() throws Exception {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        this.documentBuilderFactory = factory;
    }

    /**
     * Fetch articles from every feed registered for this topic, merged in
     * declaration order, plus the {@link LabBlogRegistry#GLOBAL_FEEDS} catch-alls
     * (deduplicated — a global feed already listed for the topic is fetched once).
     * Global feeds are what give feed-less topics (e.g. Grok, IDE agents) any
     * first-party signal at all.
     */
    public List<Map<String, String>> fetchForTopic(String topic) {
        java.util.LinkedHashSet<String> feeds = new java.util.LinkedHashSet<>(LabBlogRegistry.feedsForTopic(topic));
        feeds.addAll(LabBlogRegistry.GLOBAL_FEEDS);
        List<Map<String, String>> combined = new ArrayList<>();
        for (String feedUrl : feeds) {
            combined.addAll(fetchFeed(feedUrl));
        }
        return combined;
    }

    private List<Map<String, String>> fetchFeed(String feedUrl) {
        CachedFeed cached = feedCache.get(feedUrl);
        if (cached != null && System.nanoTime() - cached.fetchedAtNanos() < FEED_TTL.toNanos()) {
            return cached.items();
        }
        List<Map<String, String>> items = download(feedUrl);
        if (items != null) {
            feedCache.put(feedUrl, new CachedFeed(items, System.nanoTime()));
            return items;
        }
        return List.of();
    }

    /** The parsed items, or null when the fetch failed (so it is not memoized). */
    private List<Map<String, String>> download(String feedUrl) {
        try {
            // Streamed with a byte ceiling (see MAX_FEED_BYTES) rather than
            // exchange(..., byte[].class), which would buffer an unbounded body
            // before we could look at its size.
            byte[] body = restTemplate.execute(feedUrl, HttpMethod.GET,
                    request -> {
                        request.getHeaders().setAccept(List.of(
                                MediaType.APPLICATION_XML, MediaType.APPLICATION_ATOM_XML, MediaType.TEXT_XML));
                        request.getHeaders().set("User-Agent", "curio-news/1.0 (+https://curio-news.dev)");
                    },
                    response -> readBounded(response.getBody(), feedUrl));
            if (body == null || body.length == 0) {
                recordEmpty(feedUrl);
                return List.of();
            }

            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(body));
            doc.getDocumentElement().normalize();

            String root = doc.getDocumentElement().getNodeName().toLowerCase();
            List<Map<String, String>> items = "feed".equals(root)
                    ? parseAtom(doc, feedUrl)
                    : parseRss(doc, feedUrl);
            if (items.isEmpty()) {
                recordEmpty(feedUrl);
                log.info("Lab blog fetch returned no items: {}", feedUrl);
            } else {
                recordSuccess(feedUrl, items.size());
                log.info("Lab blog fetch ok: {} ({} items)", feedUrl, items.size());
            }
            return items;
        } catch (Exception e) {
            recordError(feedUrl);
            // Truncate body since some 404 pages return entire HTML and flood the log.
            String msg = e.getMessage();
            if (msg != null && msg.length() > 200) {
                msg = msg.substring(0, 200) + "…";
            }
            log.warn("Lab blog fetch failed for {}: {}", feedUrl, msg);
            return null;
        }
    }

    /** Read up to {@link #MAX_FEED_BYTES}; anything larger is rejected, not truncated. */
    private byte[] readBounded(java.io.InputStream in, String feedUrl) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(64 * 1024);
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            if (out.size() + n > MAX_FEED_BYTES) {
                throw new java.io.IOException("feed exceeds " + MAX_FEED_BYTES + " byte cap: " + feedUrl);
            }
            out.write(chunk, 0, n);
        }
        return out.toByteArray();
    }

    private void recordSuccess(String feedUrl, int items) {
        if (meterRegistry != null) {
            meterRegistry.counter("curio.labblog.fetch", "feed", feedUrl, "result", "success").increment();
            meterRegistry.counter("curio.labblog.items", "feed", feedUrl).increment(items);
        }
    }

    private void recordEmpty(String feedUrl) {
        if (meterRegistry != null) {
            meterRegistry.counter("curio.labblog.fetch", "feed", feedUrl, "result", "empty").increment();
        }
    }

    private void recordError(String feedUrl) {
        if (meterRegistry != null) {
            meterRegistry.counter("curio.labblog.fetch", "feed", feedUrl, "result", "error").increment();
        }
    }

    private List<Map<String, String>> parseRss(Document doc, String feedUrl) {
        String sourceName = firstTextByTag(doc.getDocumentElement(), "title", feedUrl);
        NodeList items = doc.getElementsByTagName("item");
        List<Map<String, String>> out = new ArrayList<>();
        int limit = Math.min(items.getLength(), MAX_ITEMS_PER_FEED);
        for (int i = 0; i < limit; i++) {
            Element item = (Element) items.item(i);
            Map<String, String> row = new LinkedHashMap<>();
            row.put("title", firstTextByTag(item, "title", ""));
            row.put("description", stripHtml(firstTextByTag(item, "description", "")));
            row.put("url", firstTextByTag(item, "link", ""));
            row.put("sourceName", sourceName);
            row.put("publishedAt", firstTextByTag(item, "pubDate", ""));
            if (!row.get("title").isBlank()) {
                out.add(row);
            }
        }
        return out;
    }

    private List<Map<String, String>> parseAtom(Document doc, String feedUrl) {
        String sourceName = firstTextByTag(doc.getDocumentElement(), "title", feedUrl);
        NodeList entries = doc.getElementsByTagName("entry");
        List<Map<String, String>> out = new ArrayList<>();
        int limit = Math.min(entries.getLength(), MAX_ITEMS_PER_FEED);
        for (int i = 0; i < limit; i++) {
            Element entry = (Element) entries.item(i);
            Map<String, String> row = new LinkedHashMap<>();
            row.put("title", firstTextByTag(entry, "title", ""));
            String summary = firstTextByTag(entry, "summary", "");
            if (summary.isBlank()) {
                summary = firstTextByTag(entry, "content", "");
            }
            row.put("description", stripHtml(summary));
            row.put("url", firstLinkHref(entry));
            row.put("sourceName", sourceName);
            row.put("publishedAt", firstTextByTag(entry, "updated", firstTextByTag(entry, "published", "")));
            if (!row.get("title").isBlank()) {
                out.add(row);
            }
        }
        return out;
    }

    private String firstTextByTag(Element parent, String tag, String fallback) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return fallback;
        Node node = list.item(0);
        String text = node.getTextContent();
        return text == null ? fallback : text.trim();
    }

    private String firstLinkHref(Element entry) {
        NodeList links = entry.getElementsByTagName("link");
        // Atom entries often carry several <link>s (rel="self"/"edit"/"replies"
        // alongside the article). rel="alternate" — or a missing rel, which the
        // Atom spec defines as alternate — is the article URL; prefer it and only
        // fall back to whatever link has an href at all.
        String fallback = "";
        for (int i = 0; i < links.getLength(); i++) {
            Element link = (Element) links.item(i);
            String href = link.getAttribute("href");
            if (href == null || href.isBlank()) {
                String text = link.getTextContent();
                href = (text == null) ? "" : text.trim();
            }
            if (href.isBlank()) {
                continue;
            }
            String rel = link.getAttribute("rel");
            if (rel == null || rel.isBlank() || "alternate".equals(rel)) {
                return href;
            }
            if (fallback.isBlank()) {
                fallback = href;
            }
        }
        return fallback;
    }

    private String stripHtml(String raw) {
        if (raw == null) return "";
        String noTags = raw.replaceAll("<[^>]+>", " ");
        return noTags.replaceAll("\\s+", " ").trim();
    }
}
