package com.curio.news.service;

import com.curio.news.port.out.AiService.DifficultyHint;
import com.curio.shared.i18n.Language;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiPromptsTest {

    @Test
    void summaryPrompt_carriesTheTopic_sources_andLanguageBlock() {
        String prompt = AiPrompts.summaryPrompt("Mistral", Language.EN, AiPrompts.SourceMode.ARTICLES, "[1] Title: one");

        assertThat(prompt)
                .contains("summaries for the topic: Mistral.")
                .contains("Prefer changes that happened in the last 14 days.")
                .contains("Use ONLY the numbered source articles below")
                .contains("Source articles:\n[1] Title: one")
                .contains("\"source\": 1,")
                .doesNotContain("source_url")
                .contains("in English")
                .contains("\"topic\": \"Mistral\"")
                .doesNotContain("한국어");
    }

    @Test
    void summaryPrompt_asksForAOneToFiveImportance_inBothSourceModes() {
        for (AiPrompts.SourceMode mode : AiPrompts.SourceMode.values()) {
            assertThat(AiPrompts.summaryPrompt("Mistral", Language.EN, mode, ""))
                    .contains("\"importance\": an integer 1-5")
                    .contains("5 = a major new model")
                    .contains("1 = a minor update")
                    .contains("\"importance\": 4,");
        }
    }

    @Test
    void summaryPrompt_scansForBrandNewModels_onTheEmergingTopic() {
        String prompt = AiPrompts.summaryPrompt("New & Emerging Models", Language.EN, AiPrompts.SourceMode.ARTICLES, "");

        assertThat(prompt).contains("brand-new frontier or agentic model launches in the last 7 days")
                .doesNotContain("last 14 days");
    }

    @Test
    void koreanEdition_asksForKoreanInTheFriendlyRegister_andKeepsJsonKeys() {
        assertThat(AiPrompts.summaryPrompt("Mistral", Language.KO, AiPrompts.SourceMode.ARTICLES, ""))
                .contains("한국어").contains("해요체").contains("\"topic\": \"Mistral\"");
        assertThat(AiPrompts.quizPrompt("digest", Language.KO, DifficultyHint.NORMAL))
                .contains("한국어").contains("해요체").contains("all JSON keys stay exactly as specified");
    }

    @Test
    void summaryPrompt_asksForTheExactPageUrl_whenSearchingTheWeb() {
        assertThat(AiPrompts.summaryPrompt("Grok (xAI)", Language.EN, AiPrompts.SourceMode.WEB_SEARCH, ""))
                .contains("Search the web")
                .contains("exact URL")
                .contains("\"source_url\"")
                .doesNotContain("Source articles:")
                .doesNotContain("\"also\"");
    }

    @Test
    void summaryPrompt_writesForReadersWhoFollowAi_andGroupsOneStoryAcrossArticles() {
        String prompt = AiPrompts.summaryPrompt("Mistral", Language.EN, AiPrompts.SourceMode.ARTICLES, "[1] Title: one");
        assertThat(prompt)
                .contains("Your readers follow AI closely")
                .contains("version numbers")
                .contains("prices")
                .contains("Never round, guess or add numbers")
                .contains("Each summary covers one announcement.")
                .contains("\"also\": [4],")
                .doesNotContain("NON-EXPERT");
    }

    @Test
    void quizPrompt_quotesTheDigest_andMixesDifficultyFromTheHint() {
        assertThat(AiPrompts.quizPrompt("the digest text", Language.EN, DifficultyHint.HARDER))
                .contains("Digest content:\nthe digest text")
                .contains("Mix difficulty: 0 easy, 2 medium, 3 challenging")
                .contains("in English");
        assertThat(AiPrompts.quizPrompt("d", Language.EN, DifficultyHint.EASIER))
                .contains("3 easy, 2 medium, 0 challenging");
    }

    @Test
    void missingHintOrLanguage_defaultToNormalEnglish() {
        assertThat(AiPrompts.difficultyMix(null)).isEqualTo("2 easy, 2 medium, 1 challenging");
        assertThat(AiPrompts.summaryLanguageInstruction(null)).contains("in English");
        assertThat(AiPrompts.quizLanguageInstruction(null)).contains("in English");
    }
}
