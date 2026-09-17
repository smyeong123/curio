package com.curio.user.dto;

import com.curio.shared.i18n.Language;
import com.curio.user.entity.UserPreferences;

import java.util.List;

/**
 * Body of {@code GET/PUT /api/v1/user/preferences}: the topics plus delivery settings
 * for one user. A user who has never saved preferences gets the platform defaults
 * ({@link #defaults()}) so the settings screen always has something to render.
 *
 * @param topics       the beats the digest is built from (never null, may be empty)
 * @param timezone     IANA zone id the digest is scheduled in; null = UTC
 * @param deliveryHour preferred delivery hour 0-23 in that zone; null = 08:00
 * @param timezoneAuto true when the timezone follows the device; false when pinned
 * @param language     edition code ("en" | "ko") the digest, quiz and email are written in
 */
public record PreferencesResponse(
        List<String> topics,
        String timezone,
        Integer deliveryHour,
        boolean timezoneAuto,
        String language) {

    public static PreferencesResponse from(UserPreferences preferences) {
        return new PreferencesResponse(
                preferences.getTopics() != null ? List.of(preferences.getTopics()) : List.of(),
                preferences.getTimezone(),
                preferences.getDeliveryHour(),
                preferences.isTimezoneAuto(),
                Language.fromCode(preferences.getLanguage()).code());
    }

    /** What a user sees before they have saved any preferences. */
    public static PreferencesResponse defaults() {
        return new PreferencesResponse(List.of(), null, null, true, Language.DEFAULT.code());
    }
}
