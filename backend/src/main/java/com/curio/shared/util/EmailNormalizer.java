package com.curio.shared.util;

import java.util.Locale;

/**
 * Canonicalizes email addresses so a single real mailbox maps to exactly one
 * account. Without this, {@code Victim@Gmail.com} and {@code victim@gmail.com}
 * are distinct rows (the users.email unique constraint is case-sensitive), which
 * lets an attacker pre-register a victim's address in a different case and slip
 * past the case-sensitive lookups in registration, login, password reset, and
 * the Google-merge anti-squatting defense.
 *
 * Applied at every read and write boundary; a DB-level unique index on
 * {@code lower(email)} (migration V27) backs it up if a future path forgets.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {}

    /** Trim surrounding whitespace and lowercase. Null-safe (returns null). */
    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
