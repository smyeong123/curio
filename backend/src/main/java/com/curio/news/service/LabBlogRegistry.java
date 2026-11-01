package com.curio.news.service;

import java.util.List;
import java.util.Map;

/**
 * Maps Curio topics to the official RSS/Atom feeds of the relevant AI labs.
 * {@link LabBlogFetcher#fetchForTopic} fetches every feed listed for a topic
 * and merges the results; a feed that fails is logged and skipped while the
 * others still contribute.
 *
 * A small set of feeds intentionally — the goal is to surface first-party
 * release-note / product-update signal, then fall back to NewsAPI for the
 * long tail.
 *
 * Feeds rot: labs move or drop their RSS without notice and a dead feed fails
 * quietly per fetch, so recheck these URLs when digests thin out (the
 * {@code curio.labblog.fetch} meter shows per-feed errors).
 *
 * Keep the string keys in sync with TopicConstants.ALL_TOPICS.
 */
public final class LabBlogRegistry {

    private LabBlogRegistry() {}

    /**
     * Anthropic publishes no RSS of its own. This feed is generated from
     * anthropic.com/news by the community-maintained Olshansk/rss-feeds project, so
     * every item links straight to the anthropic.com post. (Google News RSS was used
     * before, but its links are news.google.com redirects, not the original article,
     * and its terms limit the feed to personal, non-commercial use.)
     *
     * Meta has no live first-party feed (the Meta Research feed stopped in 2023), so
     * Llama news comes from the Hugging Face blog and NewsAPI.
     */
    private static final String ANTHROPIC_NEWS =
            "https://raw.githubusercontent.com/Olshansk/rss-feeds/main/feeds/feed_anthropic_news.xml";
    private static final String OPENAI_BLOG = "https://openai.com/blog/rss.xml";
    private static final String DEEPMIND_BLOG = "https://deepmind.google/blog/rss.xml";
    private static final String MISTRAL_NEWS = "https://mistral.ai/rss.xml";
    private static final String HUGGINGFACE_BLOG = "https://huggingface.co/blog/feed.xml";

    /**
     * Global feeds consulted for every topic — these are the best single source
     * for brand-new-model discovery (Hugging Face blog in particular indexes
     * most open-weight launches).
     */
    public static final List<String> GLOBAL_FEEDS = List.of(HUGGINGFACE_BLOG);

    public static final Map<String, List<String>> TOPIC_FEEDS = Map.ofEntries(
            // Proprietary Frontier
            Map.entry("Claude (Anthropic)", List.of(ANTHROPIC_NEWS)),
            Map.entry("GPT & ChatGPT (OpenAI)", List.of(OPENAI_BLOG)),
            Map.entry("Gemini (Google DeepMind)", List.of(DEEPMIND_BLOG)),
            Map.entry("Grok (xAI)", List.of()),
            // Open-Weight Leaders
            Map.entry("Llama (Meta AI)", List.of(HUGGINGFACE_BLOG)),
            Map.entry("DeepSeek", List.of(HUGGINGFACE_BLOG)),
            Map.entry("Qwen (Alibaba)", List.of(HUGGINGFACE_BLOG)),
            Map.entry("Mistral", List.of(MISTRAL_NEWS, HUGGINGFACE_BLOG)),
            // Coding Agents
            Map.entry("Claude Code & CLI Agents", List.of(ANTHROPIC_NEWS)),
            Map.entry("Cursor, Aider & IDE Agents", List.of()),
            Map.entry("Devin & Autonomous Coders", List.of()),
            // Agent Platforms
            Map.entry("Browser & Computer-Use Agents", List.of(ANTHROPIC_NEWS, OPENAI_BLOG)),
            Map.entry("Agent Frameworks & SDKs", List.of(ANTHROPIC_NEWS, OPENAI_BLOG)),
            // Capabilities & Ecosystem
            Map.entry("Reasoning & Context", List.of(ANTHROPIC_NEWS, OPENAI_BLOG, DEEPMIND_BLOG)),
            Map.entry("Multimodal (Vision, Audio, Video)", List.of(OPENAI_BLOG, DEEPMIND_BLOG)),
            Map.entry("Pricing & Availability", List.of(ANTHROPIC_NEWS, OPENAI_BLOG, DEEPMIND_BLOG)),
            Map.entry("Benchmarks & Evaluations", List.of(ANTHROPIC_NEWS, OPENAI_BLOG, DEEPMIND_BLOG, HUGGINGFACE_BLOG)),
            // Emerging (catch-all — broadest coverage)
            Map.entry("New & Emerging Models",
                    List.of(ANTHROPIC_NEWS, OPENAI_BLOG, DEEPMIND_BLOG, MISTRAL_NEWS, HUGGINGFACE_BLOG))
    );

    public static List<String> feedsForTopic(String topic) {
        return TOPIC_FEEDS.getOrDefault(topic, List.of());
    }
}
