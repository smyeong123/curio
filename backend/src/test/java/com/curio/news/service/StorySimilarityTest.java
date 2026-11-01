package com.curio.news.service;

import com.curio.news.service.StorySimilarity.Fingerprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixtures are real digest stories from production (2026-10-14 to 11-02, two readers), headline and
 * summary verbatim. The repeats are what readers actually got twice; the near-misses are distinct
 * stories an earlier version of this matcher wrongly paired.
 */
class StorySimilarityTest {

    /** 11-01, https://mistral.ai/news/mistral-large-4-oss/ */
    static final Fingerprint MISTRAL_1101 = StorySimilarity.fingerprint(
            "Mistral Releases 'Le Chonk' — A Massive Open-Weight AI Anyone Can Download",
            "French AI lab Mistral released Mistral Large 4, nicknamed 'Le Chonk,' as a free, open-weight model — meaning anyone can download and run it. It has 1 trillion parameters (think of those as the knobs that make the model smarter) and handles text and images. Mistral says it beats other freely available models at coding tasks and was built entirely in Europe.");
    /** 11-02, https://cryptobriefing.com/mistral-ai-large-4-cybersecurity-model/ */
    static final Fingerprint MISTRAL_1102 = StorySimilarity.fingerprint(
            "Mistral Large 4 ships as open-weight, trillion-parameter model targeting cybersecurity",
            "Mistral released Large 4 (ML4, 'Le Chonk') on October 29 — a ~1T-parameter MoE model with 52B active parameters per token, 1M-token context window, and support for 160+ languages. Weights are on Hugging Face under a modified MIT-style license; it runs on as few as four Nvidia B200/B300 GPUs. API pricing is $0.70/M input and $2.10/M output tokens. Primary pitch is cybersecurity: vulnerability detection, patching, and agentic coding.");
    /** 10-31, https://thehackernews.com/2026/10/official-claude-code-extension-on.html */
    static final Fingerprint EXTENSION_1031 = StorySimilarity.fingerprint(
            "Fake Claude Coding Tool Installed Spy Software on Developers' Computers",
            "A malicious version of a popular Claude coding extension appeared on the Cursor app marketplace. Developers who downloaded it unknowingly installed a backdoor — a hidden program that lets outsiders access their computer remotely. Anthropic was not directly hacked, but attackers impersonated their official tool. The fake extension has since been removed, but any developer who installed it may still be compromised.");
    /** 11-01, https://www.wired.com/story/supply-chain-attack-malicious-anthropic-claude-extensions/ */
    static final Fingerprint EXTENSION_1101 = StorySimilarity.fingerprint(
            "Malicious Fake Claude Extensions Installed on Thousands of Devices in Supply Chain Attack",
            "Attackers secretly inserted harmful code into third-party browser extensions (add-ons) that claimed to work with Claude. When users installed these extensions, malware ended up on their devices without their knowledge. This type of attack — called a supply chain attack — targets the tools people use rather than Claude itself. Thousands of devices were affected.");
    /** 11-02, https://phorion.io/blog/offical-claude-code-vsx-compromise/ */
    static final Fingerprint EXTENSION_1102 = StorySimilarity.fingerprint(
            "Backdoor Found in Official Claude Code VS Code Extension Versions 2.1.312 and 2.1.313 on Cursor Marketplace",
            "Phorion discovered a supply-chain compromise in the official Claude Code for VS Code extension on the Cursor Marketplace. Versions 2.1.312 and 2.1.313 contained a persistent SSH backdoor targeting macOS, Linux, and Windows — dropping SSH keys and persistence logic on install. Cursor has removed both versions. A macOS-specific bug in the malware's SSH-enable check may have limited the inbound access path on machines without SSH already running.");
    /** 10-22, https://deepmind.google/blog/introducing-gemini-4-argon/ */
    static final Fingerprint ARGON_LAUNCH = StorySimilarity.fingerprint(
            "Google's Gemini 4 Argon Launches as a Model Built for Tough, Multi-Step Problems",
            "Google DeepMind released Gemini 4 Argon, a new AI model designed to handle complex, hard-to-solve tasks. It's also being adapted for robotics, meaning it can reason about physical actions in the real world. DeepMind published a separate post on how they approached safety during development, signaling that this is a more powerful model with extra precautions built in.");
    /** 10-23, https://deepmind.google/blog/gemini-4-argon-for-robotics/ */
    static final Fingerprint ARGON_ROBOTICS = StorySimilarity.fingerprint(
            "Google DeepMind Releases Gemini 4 Argon, Including a Robotics-Focused Version",
            "Google DeepMind launched Gemini 4 Argon, a new AI model with a dedicated version built for robots. The robotics edition helps machines better understand and interact with the physical world. DeepMind also published a safety report explaining how they tested the model for risks before releasing it. This marks a significant step toward AI that can operate in real environments, not just on screens.");
    /** 10-31, https://deepmind.google/blog/gemini-4-carbon-our-next-frontier-model/ */
    static final Fingerprint CARBON_LAUNCH = StorySimilarity.fingerprint(
            "Google DeepMind Announces Gemini 4 Carbon, Its Next Major AI Model",
            "Google DeepMind revealed Gemini 4 Carbon, its next big AI model. The announcement signals a significant step up from current Gemini versions. Details are still emerging, but this is positioned as a frontier model — meaning it's designed to push the boundaries of what AI can do across tasks like reasoning, writing, and problem-solving.");
    /** 11-01, https://deepmind.google/blog/genie-4-a-new-generation-of-world-simulation/ */
    static final Fingerprint GENIE_4 = StorySimilarity.fingerprint(
            "Google DeepMind's Genie 4 Can Simulate Realistic Interactive Worlds in Real Time",
            "Google DeepMind launched Genie 4, a new AI system that can generate detailed, interactive virtual environments — think realistic video-game-like worlds — on the fly. Previous versions were more limited and less lifelike. The new system produces significantly more realistic simulations that respond to user input in real time.");
    /** 11-02, https://cryptobriefing.com/google-deepmind-dancing-robot-text-prompt/ */
    static final Fingerprint DANCING_ROBOT = StorySimilarity.fingerprint(
            "DeepMind demos AI-designed dancing robot built end-to-end from a single text prompt using Gemini 4 Argon and Antigravity",
            "On October 31, Google DeepMind showed a physical 12-DoF dancing robot designed, simulated, and built entirely by AI agents — no manual CAD. Gemini 4 Argon handled complex multi-step reasoning and robotics simulation, while the Antigravity platform orchestrated the autonomous coding and multi-system workflow. Humans acted only in a supervisory role, illustrating a shift toward agent-first robotics development environments.");
    /** 10-21, https://openai.com/index/openai-to-acquire-orchestra-bio */
    static final Fingerprint OPENAI_DRUGS = StorySimilarity.fingerprint(
            "OpenAI Acquires a Life Sciences Company to Speed Up Drug Discovery",
            "OpenAI is buying Orchestra Bio, a company focused on using AI to help pharmaceutical researchers develop new drugs faster. The goal is to bring OpenAI's AI tools directly into biotech and drug development workflows. This is one of OpenAI's most significant moves into the healthcare and science space.");
    /** 10-31, https://www.anthropic.com */
    static final Fingerprint ANTHROPIC_DRUGS = StorySimilarity.fingerprint(
            "Anthropic Uses Claude to Help Speed Up Drug Discovery",
            "Anthropic shared early results showing Claude is being used to help scientists develop new medicines faster. The AI helps researchers analyze complex biological data, a task that normally takes teams of experts months. Anthropic also expanded its 'AI for Science' program to 20 research partners, giving more labs access to Claude for scientific work. Forbes noted this marks a big move beyond software into life sciences.");
    /** 10-14, https://deepmind.google/blog/embeddinggemma-2-an-open-lightweight-multimodal-embedding-model/ */
    static final Fingerprint DEEPMIND_EMBEDDING = StorySimilarity.fingerprint(
            "Google DeepMind Launches a Free, Open Multimodal Embedding Model",
            "Google DeepMind released EmbeddingGemma 2, a lightweight open model that can understand and compare both text and images together. 'Embedding' means turning content into a format computers can search and compare. Because it's open and small, developers can run it cheaply or even on their own hardware, making image-and-text search easier to build into apps.");
    /** 10-16, https://huggingface.co/blog/meta-muse-spark-open */
    static final Fingerprint META_MULTIMODAL = StorySimilarity.fingerprint(
            "Meta Releases Open Multimodal AI Model That Handles Text, Images, and More",
            "Meta launched Muse Spark Open, a free-to-download AI model that can understand and generate multiple types of content — text, images, and more — all at once. It uses a 'mixture-of-experts' design, meaning it activates only the parts of the model needed for a given task, making it more efficient. Being open-weight means anyone can download and run it themselves.");
    /** 11-02, https://www.anthropic.com/news/expanding-partnership-gsa */
    static final Fingerprint GOV_DISCOUNT = StorySimilarity.fingerprint(
            "Anthropic Gives Government Agencies 40% Discount on Claude Enterprise, API Across All Product Surfaces",
            "Anthropic expanded its GSA partnership to give US federal, state, local, tribal, and territorial government agencies a 40% discount on Claude Enterprise, Claude for Government, and the Claude API — covering direct purchases and cloud-provider channels. No minimum spend commitment; agencies pay for consumed usage. Discounts are estimated to save government $1B total. ~500 government organizations already use Claude; weekly Claude for Government active users are up ~8x since February.");
    /** 11-02, https://www.anthropic.com/news/ai-for-science-expands-foundations-research-institutions */
    static final Fingerprint SCIENCE_CREDITS = StorySimilarity.fingerprint(
            "Anthropic Expands AI for Science Program With Free Claude Credits to 20 Research Institutions",
            "Anthropic's AI for Science program now partners with 20 scientific foundations, institutions, and research groups globally, providing free Claude credits for high-impact scientific work. The program, launched May 2025, has grown to include a discounted Team plan for scientists and has supported hundreds of projects across biology, medicine, physics, chemistry, climate science, and mathematics. Institutional partnerships aim to produce open datasets and open-source software as public goods.");
    /** 11-02, https://www.anthropic.com/news/s1-sec */
    static final Fingerprint IPO_S1 = StorySimilarity.fingerprint(
            "Anthropic Files S-1 for IPO on Nasdaq Under Ticker 'ANT'",
            "Anthropic, PBC filed an S-1 registration statement with the SEC for a proposed IPO of Class A common stock, to be listed on the Nasdaq Global Select Market under ticker 'ANT'. Share count and price range have not been determined. Goldman Sachs and Morgan Stanley are underwriters. The registration is not yet effective; no securities can be sold until it is.");
    /** 11-02, https://claude.com/resources/articles/smart-reports-surveys-pooled-budgets */
    static final Fingerprint ENTERPRISE_REPORTS = StorySimilarity.fingerprint(
            "Claude Enterprise Gets Smart Reports, In-App Surveys, and Pooled Group Budgets",
            "Anthropic has made three new features generally available for Claude Enterprise: Smart Reports analyze team transcripts to surface workstreams, spend per workflow, friction points, and repeatable workflow recommendations; in-app surveys capture user feedback on Claude's impact; and pooled group budgets let admins set one shared spending limit per team. Team leads without admin rights can run reports for their own groups. Reports are not intended for individual performance evaluation.");
    /** 10-14, https://arstechnica.com/ai/2026/10/anthropics-claude-ai-submitted-false-murder-tip-to-philadelphia-police/ */
    static final Fingerprint MURDER_TIP = StorySimilarity.fingerprint(
            "Claude Sent a False Murder Tip to Philadelphia Police, Raising Safety Concerns",
            "Claude reportedly submitted a fabricated tip about a murder to Philadelphia police, an incident that highlights real dangers when AI is used in high-stakes situations. The AI 'hallucinated' — meaning it confidently made up false information — with serious real-world consequences. Anthropic has not yet publicly explained how it happened or what safeguards it is adding.");
    /** 10-16, https://arstechnica.com/ */
    static final Fingerprint MURDER_TIP_AGAIN = StorySimilarity.fingerprint(
            "Claude AI Submitted a Fake Murder Tip to Philadelphia Police",
            "During a testing scenario, Anthropic's Claude AI made up a tip about an unsolved murder and sent it to Philadelphia police. The tip was false. This raises real concerns about AI systems taking actions in the real world — like contacting authorities — without proper checks. Anthropic has not yet explained exactly how or why this happened.");
    /** 10-15, https://www.theregister.com/ */
    static final Fingerprint CODE_OUTPUT = StorySimilarity.fingerprint(
            "Claude's Code Output Is So Massive, Anthropic Is Rethinking How Software Is Stored",
            "Claude is generating so much code — the instructions that make software run — that Anthropic is reportedly rethinking source control, the systems developers use to track and manage code changes. Meanwhile, a separate incident revealed that Claude, acting as an AI agent, submitted a false murder tip to Philadelphia police, highlighting real risks when AI systems take real-world actions without enough oversight.");
    /** 10-17, https://huggingface.co/blog/ibm-granite/granite-switch-4-1 */
    static final Fingerprint GRANITE_SWITCH = StorySimilarity.fingerprint(
            "IBM's New AI Model Handles Chat, Decisions, and Speech From a Single System",
            "IBM released Granite Switch 4.1, a single AI model that can handle conversation, decision-making, and spoken audio — all from one access point. Usually, companies need separate tools for speech versus text tasks. Combining them into one system simplifies things for developers building apps. This is one of the first models to blend speech understanding into a general-purpose AI in this way.");
    /** 10-22, https://huggingface.co/blog/ibm-granite/granite-speech-5 */
    static final Fingerprint GRANITE_SPEECH = StorySimilarity.fingerprint(
            "IBM Releases Granite Speech 5.0 for Faster, More Accurate Audio Transcription and Translation",
            "IBM launched Granite Speech 5.0, a new AI model that converts spoken audio into text, translates speech across languages, and can identify who is speaking — all faster and more accurately than before. It's designed to work in real-world settings where audio quality isn't perfect.");

    @Test
    void theMistralLaunchFromAnotherOutlet_isTheSameStory() {
        assertThat(StorySimilarity.sameStory(MISTRAL_1101, MISTRAL_1102)).isTrue();
    }

    @Test
    void theClaudeCodeExtensionBackdoorFromThreeOutlets_isTheSameStory() {
        assertThat(StorySimilarity.sameStory(EXTENSION_1031, EXTENSION_1101)).isTrue();
        assertThat(StorySimilarity.sameStory(EXTENSION_1101, EXTENSION_1102)).isTrue();
        assertThat(StorySimilarity.sameStory(EXTENSION_1031, EXTENSION_1102)).isTrue();
    }

    @Test
    void theSameIncidentRetold_isTheSameStory_butAStoryThatMentionsItInPassingIsNot() {
        assertThat(StorySimilarity.sameStory(MURDER_TIP, MURDER_TIP_AGAIN)).isTrue();
        // The code-output story's summary retells the murder tip in its last sentence.
        assertThat(StorySimilarity.sameStory(MURDER_TIP, CODE_OUTPUT)).isFalse();
    }

    @Test
    void matchingIsSymmetric() {
        assertThat(StorySimilarity.sameStory(MISTRAL_1102, MISTRAL_1101)).isTrue();
        assertThat(StorySimilarity.sameStory(EXTENSION_1102, EXTENSION_1031)).isTrue();
    }

    @Test
    void differentModelsOfTheSameFamily_areNotTheSameStory() {
        assertThat(StorySimilarity.sameStory(ARGON_LAUNCH, CARBON_LAUNCH)).isFalse();
        assertThat(StorySimilarity.sameStory(CARBON_LAUNCH, GENIE_4)).isFalse();
        assertThat(StorySimilarity.sameStory(GRANITE_SWITCH, GRANITE_SPEECH)).isFalse();
    }

    @Test
    void aNewStoryThatUsesAnOlderModel_isNotThatModelsLaunchStory() {
        assertThat(StorySimilarity.sameStory(DANCING_ROBOT, ARGON_LAUNCH)).isFalse();
        assertThat(StorySimilarity.sameStory(DANCING_ROBOT, ARGON_ROBOTICS)).isFalse();
    }

    @Test
    void theSameThemeFromDifferentVendors_isNotTheSameStory() {
        assertThat(StorySimilarity.sameStory(OPENAI_DRUGS, ANTHROPIC_DRUGS)).isFalse();
        assertThat(StorySimilarity.sameStory(DEEPMIND_EMBEDDING, META_MULTIMODAL)).isFalse();
    }

    @Test
    void sameDaySameVendorStories_areNeverPaired() {
        List<Fingerprint> distinct = List.of(GOV_DISCOUNT, SCIENCE_CREDITS, IPO_S1, ENTERPRISE_REPORTS,
                MISTRAL_1102, EXTENSION_1102, DANCING_ROBOT);
        for (int i = 0; i < distinct.size(); i++) {
            for (int j = 0; j < distinct.size(); j++) {
                if (i == j) continue;
                assertThat(StorySimilarity.sameStory(distinct.get(i), distinct.get(j)))
                        .as("pair %d/%d", i, j).isFalse();
            }
        }
    }

    @Test
    void sizesWrittenOutMatchTheirShortForm_andProductNamesKeepTheirModelWord() {
        assertThat(StorySimilarity.fingerprint("A 1 trillion parameter model", "It has 1-trillion parameters.")
                .all()).containsKey("1t");
        assertThat(StorySimilarity.fingerprint("Google Launches Gemini 4 Argon", null).headline())
                .containsKey("gemini 4 argon").doesNotContainKey("gemini 4");
        assertThat(StorySimilarity.fingerprint("Out through 2027 with a 40% discount and 4 times faster", null)
                .all()).doesNotContainKeys("2027", "through 2027", "40%", "4");
    }

    @Test
    void emptyOrVendorOnlyText_neverMatches() {
        Fingerprint vendorOnly = StorySimilarity.fingerprint("Anthropic Claude update", "New Claude model from Anthropic.");
        assertThat(vendorOnly.isEmpty()).isTrue();
        assertThat(StorySimilarity.sameStory(vendorOnly, vendorOnly)).isFalse();
        assertThat(StorySimilarity.sameStory(StorySimilarity.fingerprint(null, null), MISTRAL_1102)).isFalse();
    }
}
