package com.curio.news.service;

import java.util.List;
import java.util.Map;

/**
 * Maps Curio topics to the official RSS/Atom feeds of the relevant AI labs.
 * Feeds are tried in order; failures are logged and the next one is tried.
 *
 * A small set of feeds intentionally — the goal is to surface first-party
 * release-note / product-update signal, then fall back to NewsAPI for the
 * long tail. Any feed that 404s is simply skipped (see {@link LabBlogFetcher}).
 *
 * Keep the string keys in sync with TopicConstants.ALL_TOPICS.
 */
public final class LabBlogRegistry {

    private LabBlogRegistry() {}

    // Anthropic + Meta no longer publish public RSS. The former HF-papers-feed
    // workaround for Anthropic started returning 401 (verified 2026-07-05), so
    // Anthropic now rides a Google News RSS query — third-party, but the only
    // live Anthropic-specific feed until they ship RSS. Meta stays on the Meta
    // Research blog feed (alive). Mistral moved their feed from /news/rss.xml
    // (404) to /rss.xml (verified 2026-07-05). All six URLs below were
    // liveness-checked 2026-07-05; failures are swallowed per-feed, so recheck
    // these when digests thin out (admin getCounters() shows per-feed errors).
    private static final String ANTHROPIC_NEWS =
            "https://news.google.com/rss/search?q=Anthropic+Claude&hl=en-US&gl=US&ceid=US:en";
    private static final String OPENAI_BLOG = "https://openai.com/blog/rss.xml";
    private static final String DEEPMIND_BLOG = "https://deepmind.google/blog/rss.xml";
    private static final String META_AI_BLOG = "https://research.facebook.com/feed/";
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
            Map.entry("Llama (Meta AI)", List.of(META_AI_BLOG, HUGGINGFACE_BLOG)),
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
            Map.entry("Multimodal (Vision, Audio, Video)", List.of(OPENAI_BLOG, DEEPMIND_BLOG, META_AI_BLOG)),
            Map.entry("Pricing & Availability", List.of(ANTHROPIC_NEWS, OPENAI_BLOG, DEEPMIND_BLOG)),
            Map.entry("Benchmarks & Evaluations", List.of(ANTHROPIC_NEWS, OPENAI_BLOG, DEEPMIND_BLOG, HUGGINGFACE_BLOG)),
            // Emerging (catch-all — broadest coverage)
            Map.entry("New & Emerging Models",
                    List.of(ANTHROPIC_NEWS, OPENAI_BLOG, DEEPMIND_BLOG, META_AI_BLOG, MISTRAL_NEWS, HUGGINGFACE_BLOG))
    );

    public static List<String> feedsForTopic(String topic) {
        return TOPIC_FEEDS.getOrDefault(topic, List.of());
    }
}
