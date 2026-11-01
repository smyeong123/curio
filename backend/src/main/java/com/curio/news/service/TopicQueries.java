package com.curio.news.service;

import java.util.List;
import java.util.Map;

/**
 * Search terms per topic for the keyword sources (NewsAPI and Hacker News). Topic ids
 * are display labels like "GPT & ChatGPT (OpenAI)"; sent as a search query verbatim
 * they match almost nothing, so each topic gets the names people actually write.
 *
 * Keep the keys in sync with TopicConstants.ALL_TOPICS (a test checks this).
 */
final class TopicQueries {

    private TopicQueries() {}

    /** NewsAPI {@code q} per topic (supports quotes, AND, OR, parentheses). */
    static final Map<String, String> NEWS_API = Map.ofEntries(
            Map.entry("Claude (Anthropic)", "Anthropic OR \"Claude AI\" OR \"Claude Opus\" OR \"Claude Sonnet\""),
            Map.entry("GPT & ChatGPT (OpenAI)", "OpenAI OR ChatGPT"),
            Map.entry("Gemini (Google DeepMind)", "\"Google Gemini\" OR DeepMind OR \"Gemini AI\""),
            Map.entry("Grok (xAI)", "xAI OR Grok"),
            Map.entry("Llama (Meta AI)", "\"Meta AI\" OR \"Meta Llama\" OR \"Llama model\""),
            Map.entry("DeepSeek", "DeepSeek"),
            Map.entry("Qwen (Alibaba)", "Qwen OR \"Alibaba AI\""),
            Map.entry("Mistral", "\"Mistral AI\""),
            Map.entry("Claude Code & CLI Agents", "\"Claude Code\" OR \"Codex CLI\" OR \"Gemini CLI\""),
            Map.entry("Cursor, Aider & IDE Agents", "\"Cursor AI\" OR Anysphere OR Windsurf OR \"GitHub Copilot\""),
            Map.entry("Devin & Autonomous Coders", "\"Cognition AI\" OR \"Devin AI\" OR \"AI software engineer\""),
            Map.entry("Browser & Computer-Use Agents", "\"computer use\" OR \"browser agent\" OR \"AI agent browser\""),
            Map.entry("Agent Frameworks & SDKs", "\"Agents SDK\" OR \"Agent SDK\" OR \"Model Context Protocol\" OR LangChain"),
            Map.entry("Reasoning & Context", "\"reasoning model\" OR \"context window\""),
            Map.entry("Multimodal (Vision, Audio, Video)", "\"AI video model\" OR \"image generation model\" OR \"voice model\" OR Sora OR Veo"),
            Map.entry("Pricing & Availability", "(\"API pricing\" OR \"price cut\") AND (OpenAI OR Anthropic OR Gemini)"),
            Map.entry("Benchmarks & Evaluations", "\"AI benchmark\" OR \"LLM benchmark\" OR \"SWE-bench\" OR \"ARC-AGI\""),
            Map.entry("New & Emerging Models", "\"new AI model\" OR \"open-weight model\" OR \"open-source model\"")
    );

    /** Hacker News phrases per topic; each phrase is one exact-phrase search. */
    static final Map<String, List<String>> HACKER_NEWS = Map.ofEntries(
            Map.entry("Claude (Anthropic)", List.of("Anthropic", "Claude")),
            Map.entry("GPT & ChatGPT (OpenAI)", List.of("OpenAI", "ChatGPT")),
            Map.entry("Gemini (Google DeepMind)", List.of("Gemini", "DeepMind")),
            Map.entry("Grok (xAI)", List.of("Grok", "xAI")),
            Map.entry("Llama (Meta AI)", List.of("Llama")),
            Map.entry("DeepSeek", List.of("DeepSeek")),
            Map.entry("Qwen (Alibaba)", List.of("Qwen")),
            Map.entry("Mistral", List.of("Mistral")),
            Map.entry("Claude Code & CLI Agents", List.of("Claude Code", "Codex CLI", "Gemini CLI")),
            Map.entry("Cursor, Aider & IDE Agents", List.of("Cursor", "Windsurf", "Aider", "Copilot")),
            Map.entry("Devin & Autonomous Coders", List.of("Devin", "Cognition")),
            Map.entry("Browser & Computer-Use Agents", List.of("computer use", "browser agent")),
            Map.entry("Agent Frameworks & SDKs", List.of("Agents SDK", "MCP", "LangChain")),
            Map.entry("Reasoning & Context", List.of("reasoning model", "context window")),
            Map.entry("Multimodal (Vision, Audio, Video)", List.of("Sora", "Veo", "image model", "video model")),
            Map.entry("Pricing & Availability", List.of("API pricing")),
            Map.entry("Benchmarks & Evaluations", List.of("LLM benchmark", "SWE-bench", "ARC-AGI")),
            Map.entry("New & Emerging Models", List.of("open weights", "open-weight model"))
    );

    /** The topic label minus the punctuation that breaks a keyword search, for unmapped topics. */
    static String newsApiQuery(String topic) {
        String mapped = NEWS_API.get(topic);
        return mapped != null ? mapped : topic.replaceAll("[()&,]", " ").replaceAll("\\s+", " ").trim();
    }

    static List<String> hackerNewsPhrases(String topic) {
        return HACKER_NEWS.getOrDefault(topic, List.of());
    }
}
