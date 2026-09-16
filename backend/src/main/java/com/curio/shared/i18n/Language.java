package com.curio.shared.i18n;

import java.util.Locale;
import java.util.Map;

/**
 * The editions Curio publishes in. Drives the language the AI writes digests and
 * quizzes in and the locale of the digest email.
 *
 * <p>Stored on {@code user_preferences.language} as the lowercase code ("en" / "ko") —
 * the same codes the frontend's UI locale uses — and stamped into every digest's
 * content JSON under {@link #CONTENT_KEY}, so the email and quiz for an already
 * generated digest follow the language it was <em>written</em> in, not the user's
 * current setting. Legacy digests without the key are English.
 */
public enum Language {
    EN("en", Locale.ENGLISH),
    KO("ko", Locale.KOREAN);

    public static final Language DEFAULT = EN;
    /** Key under which a digest's content JSON records its language. */
    public static final String CONTENT_KEY = "language";

    private final String code;
    private final Locale locale;

    Language(String code, Locale locale) {
        this.code = code;
        this.locale = locale;
    }

    /** Lowercase BCP-47 base code: "en" / "ko". */
    public String code() {
        return code;
    }

    public Locale locale() {
        return locale;
    }

    /**
     * Lenient parse for stored / stamped values: null, blank or unknown → the default
     * edition; case-insensitive; a region suffix ("ko-KR") is ignored.
     */
    public static Language fromCode(String code) {
        if (code == null) {
            return DEFAULT;
        }
        String base = code.trim().toLowerCase(Locale.ROOT);
        int dash = base.indexOf('-');
        if (dash > 0) {
            base = base.substring(0, dash);
        }
        for (Language language : values()) {
            if (language.code.equals(base)) {
                return language;
            }
        }
        return DEFAULT;
    }

    /** Strict check for API input: exactly "en" or "ko" (case-insensitive), nothing else. */
    public static boolean isSupportedCode(String code) {
        if (code == null) {
            return false;
        }
        String c = code.trim().toLowerCase(Locale.ROOT);
        for (Language language : values()) {
            if (language.code.equals(c)) {
                return true;
            }
        }
        return false;
    }

    public static Language orDefault(Language language) {
        return language == null ? DEFAULT : language;
    }

    /** The language a digest was generated in, read from its content JSON. */
    public static Language fromDigestContent(Map<String, Object> content) {
        if (content == null) {
            return DEFAULT;
        }
        Object value = content.get(CONTENT_KEY);
        return value == null ? DEFAULT : fromCode(value.toString());
    }
}
