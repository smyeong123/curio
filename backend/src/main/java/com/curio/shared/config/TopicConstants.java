package com.curio.shared.config;

import java.util.List;
import java.util.Set;

/**
 * Canonical list of valid Curio topics.
 *
 * Curio's focus is AI *model* news — new features, model releases, and emerging
 * agentic systems. This list mirrors {@code frontend/src/data/topics.ts}.
 * Keep the two in sync; the UserService validates submitted topics against
 * {@link #VALID_TOPICS}.
 */
public final class TopicConstants {

    private TopicConstants() {}

    // L1: Frontier Labs
    // L1: Agentic & Developer Tools
    // L1: Capabilities & Ecosystem
    // L1: Emerging
    public static final List<String> ALL_TOPICS = List.of(
            // Frontier Labs — Proprietary Frontier
            "Claude (Anthropic)",
            "GPT & ChatGPT (OpenAI)",
            "Gemini (Google DeepMind)",
            "Grok (xAI)",
            // Frontier Labs — Open-Weight Leaders
            "Llama (Meta AI)",
            "DeepSeek",
            "Qwen (Alibaba)",
            "Mistral",
            // Agentic & Developer Tools — Coding Agents
            "Claude Code & CLI Agents",
            "Cursor, Aider & IDE Agents",
            "Devin & Autonomous Coders",
            // Agentic & Developer Tools — Agent Platforms
            "Browser & Computer-Use Agents",
            "Agent Frameworks & SDKs",
            // Capabilities & Ecosystem — Model Capabilities
            "Reasoning & Context",
            "Multimodal (Vision, Audio, Video)",
            // Capabilities & Ecosystem — Market
            "Pricing & Availability",
            "Benchmarks & Evaluations",
            // Emerging — Discovery (catch-all)
            "New & Emerging Models"
    );

    public static final Set<String> VALID_TOPICS = Set.copyOf(ALL_TOPICS);
}
