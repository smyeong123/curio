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
                  blogs) over commentary as the main source. When several articles report the same
                  announcement (not merely a related one), write ONE summary, cite the best one as
                  "source" and list the others under "also". Each summary covers one announcement. Skip articles that are off-topic for %s, not news, or opinion pieces.
                  If no article fits, return an empty JSON array [].

                  Source articles:
                  %s""".formatted(topic, sourceContext)
                : """
                  Search the web for the latest news on this topic. Base every summary on one page
                  you actually opened in the search results, and give that page's exact URL — the
                  original article, not a search or aggregator page. If you find nothing current,
                  return an empty JSON array [].""";
        String sourceRule = articles
                ? "- \"source\": the number of the article the summary is based on (e.g. 3 for [3]) — never invent one\n"
                  + "            - \"also\": the numbers of other articles reporting the same story, or [] if none"
                : "- \"source_url\" and \"source_name\": the exact URL and publisher of the page the summary is based on";
        String sourceFields = articles
                ? "\"source\": 1,\n                \"also\": [4],"
                : "\"source_url\": \"https://example.com/actual-article\",\n                \"source_name\": \"Source Name\",";

        return String.format("""
            You are Curio, a curator of AI *model* news. Your readers follow AI closely —
            developers, researchers and power users who would otherwise scroll X, Reddit and
            Hacker News to keep up. They want to know exactly what shipped, without the hype.
            Generate 2-3 TL;DR summaries for the topic: %s.

            %s

            %s

            Requirements for each summary:
            - Rank by importance: new models and major capability, pricing or availability changes
              first; minor updates last. Leave out anything a close follower wouldn't care about.
            - summary: a 50-80 word TL;DR — NEVER exceed 90 words — that leads with what shipped
            - Keep the specifics the source gives: exact model and product names, version numbers,
              prices, context-window sizes, benchmark scores, and who can use it and where
              (API, app, free or paid plan, region). Never round, guess or add numbers the
              source doesn't state.
            - Use the field's normal terms (API, context window, open weights, agent) without
              explaining them; drop marketing language and filler.
            - Merge near-duplicate stories into a single summary; never cover the same announcement twice
            - headline: specific, names the model or product, no hype or clickbait
            - "why_it_matters": one sentence on what changes for someone building with or using
              these models — what they can now do, should try, or must migrate off
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
                - Register: friendly, plain 해요체 (e.g. "출시했어요", "쓸 수 있어요"), the way a Korean tech newsletter editor writes for readers who follow AI
                - Keep company, product and model names, version numbers and prices in their original form (Anthropic, Claude Sonnet 4.6, GPT-5, Gemini, Hugging Face, $3/M tokens); use the terms Korean developers use (API, 컨텍스트 윈도우, 오픈 웨이트)
                - Length: the word limits above become 120-240 Korean characters per summary (never exceed 280), in short sentences
                - JSON keys and the source, also, source_url, source_name and topic values stay exactly as specified below""";
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
