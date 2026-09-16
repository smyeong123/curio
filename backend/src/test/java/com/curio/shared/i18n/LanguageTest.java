package com.curio.shared.i18n;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageTest {

    @Test
    void fromCode_isLenient_forStoredValues() {
        assertThat(Language.fromCode("ko")).isEqualTo(Language.KO);
        assertThat(Language.fromCode("KO")).isEqualTo(Language.KO);
        assertThat(Language.fromCode(" ko-KR ")).isEqualTo(Language.KO);
        assertThat(Language.fromCode("en")).isEqualTo(Language.EN);
        assertThat(Language.fromCode(null)).isEqualTo(Language.EN);
        assertThat(Language.fromCode("")).isEqualTo(Language.EN);
        assertThat(Language.fromCode("fr")).isEqualTo(Language.EN);
    }

    @Test
    void isSupportedCode_isStrict_forApiInput() {
        assertThat(Language.isSupportedCode("en")).isTrue();
        assertThat(Language.isSupportedCode("Ko")).isTrue();
        assertThat(Language.isSupportedCode("ko-KR")).isFalse();
        assertThat(Language.isSupportedCode("fr")).isFalse();
        assertThat(Language.isSupportedCode(null)).isFalse();
    }

    @Test
    void fromDigestContent_readsTheStampedLanguage_andDefaultsLegacyDigestsToEnglish() {
        assertThat(Language.fromDigestContent(Map.of("language", "ko"))).isEqualTo(Language.KO);
        assertThat(Language.fromDigestContent(Map.of("summaries", java.util.List.of()))).isEqualTo(Language.EN);
        assertThat(Language.fromDigestContent(null)).isEqualTo(Language.EN);
    }

    @Test
    void codesAndLocales_matchTheFrontendAndJava() {
        assertThat(Language.EN.code()).isEqualTo("en");
        assertThat(Language.KO.code()).isEqualTo("ko");
        assertThat(Language.KO.locale()).isEqualTo(Locale.KOREAN);
        assertThat(Language.orDefault(null)).isEqualTo(Language.EN);
    }
}
