package com.curio.news.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
            // Bare "Sora" matched an unrelated person, and "Show HN" posts are product pages,
            // not model news (Hacker News itself comes in through HackerNewsFetcher).
            Map.entry("Multimodal (Vision, Audio, Video)", "(\"OpenAI Sora\" OR \"Google Veo\" OR \"Nano Banana\" OR Midjourney"
                    + " OR ElevenLabs OR \"video generation model\" OR \"video model\" OR \"image model\" OR \"speech model\""
                    + " OR \"voice model\" OR \"transcription model\" OR \"text-to-speech\" OR \"multimodal model\""
                    + " OR \"vision-language model\") NOT \"Show HN\""),
            Map.entry("Pricing & Availability", "(\"API pricing\" OR \"price cut\") AND (OpenAI OR Anthropic OR Gemini)"),
            Map.entry("Benchmarks & Evaluations", "\"LLM benchmark\" OR \"SWE-bench\" OR \"ARC-AGI\" OR \"Humanity's Last Exam\""
                    + " OR \"Artificial Analysis\" OR LMArena"),
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
            Map.entry("Multimodal (Vision, Audio, Video)", List.of("multimodal", "text-to-speech", "TTS",
                    "speech-to-text", "image model", "image generation", "video model", "Sora", "Veo", "ElevenLabs")),
            Map.entry("Pricing & Availability", List.of("API pricing")),
            Map.entry("Benchmarks & Evaluations", List.of("LLM benchmark", "SWE-bench", "ARC-AGI")),
            Map.entry("New & Emerging Models", List.of("open weights", "open-weight model"))
    );

    /**
     * For topics fed by general lab blogs, the words a lab post's title or description must
     * contain to count for the topic. Without this a lab feed's newest posts (policy, safety,
     * funding) fill the topic's lab slots whatever they are about. Topics not listed here
     * keep every lab post: their feeds are about the topic already (Claude, Cursor...), and
     * "New & Emerging Models" is the catch-all. Tuned against the live feeds: bare words like
     * "cost", "context", "API", "skills" or "framework" also match customer stories, education
     * programmes and security posts, so those only count in the phrases below.
     */
    static final Map<String, Pattern> LAB_RELEVANCE = Map.of(
            "Multimodal (Vision, Audio, Video)", Pattern.compile(
                    "\\b(multi-?modal|images?|video|vision|audio|speech|voices?|tts|asr|text-to-speech"
                            + "|speech-to-text|transcription|music|sora|veo|imagen|nano banana|genie|world model|omni)\\b",
                    Pattern.CASE_INSENSITIVE),
            "Reasoning & Context", Pattern.compile(
                    "\\b(reasoning|extended thinking|deep think|thinking (mode|models?|budgets?|tokens?)"
                            + "|think(ing)? (longer|harder)|chain[- ]of[- ]thought|test[- ]time (compute|scaling)"
                            + "|context windows?|long[- ]context|context length|million[- ]tokens?|\\d+[km][- ]tokens?"
                            + "|memory|o[1-9](-mini|-pro)?)\\b",
                    Pattern.CASE_INSENSITIVE),
            // "costs 40% less" is a launch post's price line; "cut costs" is a customer story.
            // Google Cloud only with an availability phrase (bare mentions are infra/partnership posts).
            "Pricing & Availability", Pattern.compile(
                    "\\b(pricing|(api|token|model) prices?|price (cuts?|drops?|reductions?)|cheaper|cost-efficient"
                            + "|costs? (up to )?\\d+% less|fraction of the cost|per million|now available"
                            + "|generally available|general availability|rate limits?|free tier|prompt caching|batch api"
                            + "|google cloud marketplace|(coming|available) (to|on|through|in) google cloud|bedrock|vertex ai|model garden|foundry|azure openai)\\b",
                    Pattern.CASE_INSENSITIVE),
            // Any "...Bench" name (SWE-bench, MentalHealthBench) but not "workbench"; "evaluations"
            // or "evals" alone is mostly safety testing, so it needs a qualifier.
            "Benchmarks & Evaluations", Pattern.compile(
                    "\\b((?!workbench)\\w*bench(marks?|marking|ed)?"
                            + "|(ai|model|llm|new|open|capability|double-blind) (evals?|evaluations?)|leaderboards?|lmarena"
                            + "|gpqa|arc-agi|mmlu|humanity's last exam|aime)\\b",
                    Pattern.CASE_INSENSITIVE),
            "Browser & Computer-Use Agents", Pattern.compile(
                    "\\b(computer[- ]use|browser[- ]use|browser agents?|(ai|agentic) browsers?|browser automation"
                            + "|browser tests?|chatgpt (agent|atlas)|atlas browser|agent mode|comet|chrome"
                            + "|web agents?|gui agents?)\\b",
                    Pattern.CASE_INSENSITIVE),
            "Agent Frameworks & SDKs", Pattern.compile(
                    "\\b(sdks?|agents? sdk|mcp|model context protocol|tool[- ]use|tool[- ]calling|function[- ]calling"
                            + "|responses api|agentkit|agent skills|agent builder|agent(ic)? (frameworks?|standards?)"
                            + "|subagents?|langgraph|langchain|llamaindex|smolagents|crewai|autogen|devday)\\b",
                    Pattern.CASE_INSENSITIVE)
    );

    /** Whether a lab-feed article belongs to the topic (always true for topics without a filter). */
    static boolean isRelevantLabArticle(String topic, Map<String, String> article) {
        Pattern pattern = LAB_RELEVANCE.get(topic);
        if (pattern == null) return true;
        String text = article.getOrDefault("title", "") + " " + article.getOrDefault("description", "");
        return pattern.matcher(text).find();
    }

    /** The topic label minus the punctuation that breaks a keyword search, for unmapped topics. */
    static String newsApiQuery(String topic) {
        String mapped = NEWS_API.get(topic);
        return mapped != null ? mapped : topic.replaceAll("[()&,]", " ").replaceAll("\\s+", " ").trim();
    }

    static List<String> hackerNewsPhrases(String topic) {
        return HACKER_NEWS.getOrDefault(topic, List.of());
    }
}
