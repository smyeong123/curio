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

    static String summaryPrompt(String topic, Language language, String sourceInstruction, String sourceContextBlock) {
        String topicInstruction = "New & Emerging Models".equals(topic)
                ? "Scan for brand-new frontier or agentic model launches in the last 7 days — including models you may not have heard of. Prioritize launches from labs outside the big five (Anthropic, OpenAI, DeepMind, xAI, Meta) and any new agentic systems. Avoid repeating models already widely covered."
                : "Focus on the most recent news, features, and releases for this topic. Prefer changes that happened in the last 14 days.";

        return String.format("""
            You are Curio, a curator of AI *model* news. Your readers track new
            features, model releases, pricing changes, and brand-new agentic systems.
            Generate 2-3 plain-language TL;DR summaries for the topic: %s.

            %s

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
            - MUST include a real, valid source_url and source_name for each summary

            %s

            Output format (JSON array):
            [
              {
                "headline": "...",
                "summary": "...",
                "why_it_matters": "...",
                "source_url": "https://example.com/actual-article",
                "source_name": "Source Name",
                "topic": "%s"
              }
            ]

            Return ONLY the JSON array, no additional text.
            """, topic, topicInstruction, sourceInstruction, sourceContextBlock, summaryLanguageInstruction(language), topic);
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
                - JSON keys, source_url, source_name and topic stay exactly as specified below""";
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
