package com.curio.news.service;

import com.curio.news.port.out.AiService.DifficultyHint;
import com.curio.shared.i18n.Language;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiPromptsTest {

    @Test
    void summaryPrompt_carriesTheTopic_sources_andLanguageBlock() {
        String prompt = AiPrompts.summaryPrompt("Mistral", Language.EN, "Use the sources.", "Source articles:\n- one");

        assertThat(prompt)
                .contains("summaries for the topic: Mistral.")
                .contains("Prefer changes that happened in the last 14 days.")
                .contains("Use the sources.")
                .contains("Source articles:\n- one")
                .contains("in English")
                .contains("\"topic\": \"Mistral\"")
                .doesNotContain("한국어");
    }

    @Test
    void summaryPrompt_scansForBrandNewModels_onTheEmergingTopic() {
        String prompt = AiPrompts.summaryPrompt("New & Emerging Models", Language.EN, "", "");

        assertThat(prompt).contains("brand-new frontier or agentic model launches in the last 7 days")
                .doesNotContain("last 14 days");
    }

    @Test
    void koreanEdition_asksForKoreanInTheFriendlyRegister_andKeepsJsonKeys() {
        assertThat(AiPrompts.summaryPrompt("Mistral", Language.KO, "", ""))
                .contains("한국어").contains("해요체").contains("\"topic\": \"Mistral\"");
        assertThat(AiPrompts.quizPrompt("digest", Language.KO, DifficultyHint.NORMAL))
                .contains("한국어").contains("해요체").contains("all JSON keys stay exactly as specified");
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
