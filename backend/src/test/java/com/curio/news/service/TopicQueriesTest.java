package com.curio.news.service;

import com.curio.shared.config.TopicConstants;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lab-post relevance filters. Titles below are real lab-feed posts (Anthropic, OpenAI, DeepMind,
 * Hugging Face) seen while tuning the patterns; the "drop" cases are the policy, partnership,
 * customer-story and education posts that used to fill the topics' lab slots.
 */
class TopicQueriesTest {

    private static Map<String, String> post(String title, String description) {
        return Map.of("title", title, "description", description == null ? "" : description);
    }

    @ParameterizedTest(name = "{0} keeps \"{1}\"")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "Reasoning & Context | Introducing Gemini 3.8 Live and 3.8 Live Extended Thinking | ",
            "Reasoning & Context | Advancing Private AI Compute with secure, server-side memory | ",
            "Reasoning & Context | New model | Supports a 1M-token context window for long documents.",
            "Reasoning & Context | Gemma 4 | Purpose-built for advanced reasoning and agentic workflows.",
            "Pricing & Availability | Introducing Claude Opus 5.5 | Leads in agentic coding and costs 40% less to run than Opus 5.",
            "Pricing & Availability | OpenAI models are coming to Google Cloud | Available through Google Cloud Marketplace.",
            "Pricing & Availability | Introducing GPT-6.1 Sol | At one-fifth of Astra's standard API input and output token prices.",
            "Pricing & Availability | Better prompt caching for GPT-6 | Higher cache hit rates.",
            "Pricing & Availability | Claude is now generally available on Amazon Bedrock | ",
            "Benchmarks & Evaluations | LifeVal | Introducing LifeVal, a benchmark measuring how well AI helps people.",
            "Benchmarks & Evaluations | Introducing MentalHealthBench | ",
            "Benchmarks & Evaluations | Piloting the world's first double-blind AI evaluations | ",
            "Benchmarks & Evaluations | Open TTS Leaderboard: Scalable Evaluation for Multilingual Text-to-Speech | ",
            "Benchmarks & Evaluations | New model | State of the art on SWE-bench Verified and ARC-AGI-2.",
            "Browser & Computer-Use Agents | Introducing GPT-6.1 Terra | Bringing fast and capable computer use to everyone",
            "Browser & Computer-Use Agents | Introducing Claude Haiku 5.5 | Built for summarization, subagents, and browser use.",
            "Browser & Computer-Use Agents | Holo4: powering generalist computer-use agents | ",
            "Browser & Computer-Use Agents | Introducing ChatGPT Atlas | ",
            "Agent Frameworks & SDKs | Donating Agent Skills to the Agentic AI Foundation | ",
            "Agent Frameworks & SDKs | Apple's Xcode now supports the Claude Agent SDK | ",
            "Agent Frameworks & SDKs | Adding MCP Tools to Reachy Mini | ",
            "Agent Frameworks & SDKs | New tools for building agents | The Responses API now supports remote MCP servers.",
    })
    void keepsOnTopicLabPosts(String topic, String title, String description) {
        assertThat(TopicQueries.isRelevantLabArticle(topic, post(title, description))).isTrue();
    }

    @ParameterizedTest(name = "{0} drops \"{1}\"")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "Reasoning & Context | Qodo builds context into AI code review with Codex | Helping developers catch P0 issues.",
            "Reasoning & Context | Teaching students to think and learn through questions | ",
            "Reasoning & Context | Anthropic files S-1 with the SEC for proposed initial public offering | ",
            "Pricing & Availability | Heimstaden is changing residential real estate management with AI | Cut costs and empower employees.",
            "Pricing & Availability | Covering electricity price increases from our data centers | ",
            "Pricing & Availability | Rapidly scaling online storage | Using Azure Cosmos DB to deliver 12x faster reads.",
            "Pricing & Availability | Helping older adults use AI in everyday life | Free, hands-on ChatGPT workshops.",
            "Benchmarks & Evaluations | An update on independent evaluators | To evaluate the safety of our systems.",
            "Benchmarks & Evaluations | Investigating three real-world incidents in our cybersecurity evaluations | ",
            "Benchmarks & Evaluations | Claude Science, an AI workbench for scientists, is now available | ",
            "Browser & Computer-Use Agents | Making it easier for websites to express AI training preferences | Website operators can opt out.",
            "Browser & Computer-Use Agents | Expanding our partnership with GSA | A new agreement for government partners.",
            "Agent Frameworks & SDKs | Two years of OpenAI Academy | Bringing AI skills to even more communities.",
            "Agent Frameworks & SDKs | More details on Fable 5's cyber safeguards and our jailbreak framework | ",
            "Agent Frameworks & SDKs | How Albertsons Companies is reimagining retail | Using ChatGPT Enterprise and the OpenAI API.",
    })
    void dropsOffTopicLabPosts(String topic, String title, String description) {
        assertThat(TopicQueries.isRelevantLabArticle(topic, post(title, description))).isFalse();
    }

    @Test
    void topicsWithoutAFilterKeepEveryLabPost() {
        Map<String, String> offTopic = post("Anthropic files S-1 with the SEC", "A registration statement.");
        assertThat(TopicQueries.isRelevantLabArticle("New & Emerging Models", offTopic)).isTrue();
        assertThat(TopicQueries.isRelevantLabArticle("Claude (Anthropic)", offTopic)).isTrue();
        assertThat(TopicQueries.LAB_RELEVANCE).doesNotContainKey("New & Emerging Models");
    }

    @Test
    void filterKeysAreRealTopics() {
        assertThat(TopicConstants.ALL_TOPICS).containsAll(TopicQueries.LAB_RELEVANCE.keySet());
    }

    @Test
    void missingTitleOrDescriptionDoesNotThrow() {
        assertThat(TopicQueries.isRelevantLabArticle("Reasoning & Context", Map.of("title", "Extended thinking"))).isTrue();
        assertThat(TopicQueries.isRelevantLabArticle("Reasoning & Context", Map.of())).isFalse();
    }
}
