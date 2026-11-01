package com.curio.news.service;

import com.curio.news.port.out.AiService.DifficultyHint;
import com.curio.shared.i18n.Language;

/**
 * The one summary prompt and one quiz prompt every provider and both key paths
 * (platform, BYOK) send. Pure functions of their inputs, so a wording change is
 * a single edit and is tested without an HTTP client.
 */
final class AiPrompts {

    private AiPrompts() {}

    /** Where a summary prompt's evidence comes from, which decides how a summary cites it. */
    enum SourceMode {
        /** Numbered articles are in the prompt; each summary cites one by number. */
        ARTICLES,
        /** No articles were found; the model searches the web and returns the page URL it used. */
        WEB_SEARCH
    }

    static String summaryPrompt(String topic, Language language, SourceMode mode, String sourceContext) {
        String topicInstruction = "New & Emerging Models".equals(topic)
                ? "Scan for brand-new frontier or agentic model launches in the last 7 days — including models you may not have heard of. Prioritize launches from labs outside the big five (Anthropic, OpenAI, DeepMind, xAI, Meta) and any new agentic systems. Avoid repeating models already widely covered."
                : "Focus on the most recent news, features, and releases for this topic. Prefer changes that happened in the last 14 days.";

        boolean articles = mode == SourceMode.ARTICLES;
        String sourceInstruction = articles
                ? """
                  Use ONLY the numbered source articles below as evidence. Base every summary on one
                  article and state only facts that article supports — when an article's text is
                  given, rely on it over its description. Prefer first-party announcements (lab
                  blogs) over commentary. Skip articles that are off-topic for %s or not news.
                  If no article fits, return an empty JSON array [].

                  Source articles:
                  %s""".formatted(topic, sourceContext)
                : """
                  Search the web for the latest news on this topic. Base every summary on one page
                  you actually opened in the search results, and give that page's exact URL — the
                  original article, not a search or aggregator page. If you find nothing current,
                  return an empty JSON array [].""";
        String sourceRule = articles
                ? "- \"source\": the number of the article the summary is based on (e.g. 3 for [3]) — never invent one"
                : "- \"source_url\" and \"source_name\": the exact URL and publisher of the page the summary is based on";
        String sourceFields = articles
                ? "\"source\": 1,"
                : "\"source_url\": \"https://example.com/actual-article\",\n                \"source_name\": \"Source Name\",";

        return String.format("""
            You are Curio, a curator of AI *model* news. Your readers track new
            features, model releases, pricing changes, and brand-new agentic systems.
            Generate 2-3 plain-language TL;DR summaries for the topic: %s.

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
            %s

            %s

            Output format (JSON array):
            [
              {
                "headline": "...",
                "summary": "...",
                "why_it_matters": "...",
                %s
                "topic": "%s"
              }
            ]

            Return ONLY the JSON array, no additional text.
            """, topic, topicInstruction, sourceInstruction, sourceRule, summaryLanguageInstruction(language), sourceFields, topic);
    }

    static String quizPrompt(String digestContent, Language language, DifficultyHint hint) {
        return String.format("""
            You are Curio's quiz generator. Based on this news digest, generate 5 multiple-choice questions to test reader comprehension.

            Digest content:
            %s

            Requirements:
            - Test factual recall and comprehension
            - 4 options per question (A, B, C, D)
            - Exactly one correct answer per question
            - Mix difficulty: %s
            - Questions should be clear and unambiguous

            %s

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
            """, digestContent, difficultyMix(hint), quizLanguageInstruction(language));
    }

    /**
     * Language block appended to every summary prompt. English is spelled out too, so
     * a source article in another language never flips the output language.
     */
    static String summaryLanguageInstruction(Language language) {
        return switch (Language.orDefault(language)) {
            case EN -> "Language: write the headline, summary and why_it_matters in English, even when a source article is in another language.";
            case KO -> """
                Language: write the headline, summary and why_it_matters in Korean (한국어), even though the sources are in English.
                - Register: friendly, plain 해요체 (e.g. "출시했어요", "쓸 수 있어요"), the way a Korean newsletter editor writes for a general reader
                - Keep company, product and model names in their original form (Anthropic, Claude, GPT-5, Gemini, Hugging Face); explain a technical term in plain Korean the first time it appears
                - Length: the word limits above become 120-220 Korean characters per summary (never exceed 260), in short sentences
                - JSON keys and the source, source_url, source_name and topic values stay exactly as specified below""";
        };
    }

    /** Language block appended to every quiz prompt. */
    static String quizLanguageInstruction(Language language) {
        return switch (Language.orDefault(language)) {
            case EN -> "Language: write every question, all four options and each explanation in English.";
            case KO -> """
                Language: write every question, all four options and each explanation in Korean (한국어), in friendly 해요체.
                - Keep company, product and model names in their original form; the option letters A-D and all JSON keys stay exactly as specified
                - The digest itself is in Korean — quiz the reader on what it says, using the same terms it uses""";
        };
    }

    /** Difficulty mix line for the quiz prompt, from the user's recent-score hint. */
    static String difficultyMix(DifficultyHint hint) {
        return switch (hint == null ? DifficultyHint.NORMAL : hint) {
            case EASIER -> "3 easy, 2 medium, 0 challenging";
            case HARDER -> "0 easy, 2 medium, 3 challenging";
            case NORMAL -> "2 easy, 2 medium, 1 challenging";
        };
    }
}
