package com.curio.shared.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The digest day: every subscriber's digest is generated once, at 05:00 Korea time,
 * and that one digest is what each reader gets at their own local delivery hour —
 * wherever they are. (Not to be confused with the EN/KO language edition.)
 *
 * <p>A digest day is named by the Korean calendar date of its 05:00 run, so it spans
 * [05:00 KST, next day 05:00 KST). It replaces the UTC calendar day as the unit of
 * "one digest per user per day": the generation job, the duplicate check, the
 * summary cache key, the V29 unique index and the email gate all agree on it.
 * Asia/Seoul has no DST, so a digest day is always exactly 24 hours.
 *
 * <p>Timestamps stay stored in UTC; only the day boundary moves.
 */
public final class DigestDay {

    /** Zone the digest day is defined in. Also the cron zone of the generation job. */
    public static final String ZONE = "Asia/Seoul";

    /** Local hour the digest day rolls over and the generation job runs. */
    public static final int ROLLOVER_HOUR = 5;

    /** Cron for the generation job — 05:00 in {@link #ZONE}. */
    public static final String GENERATION_CRON = "0 0 5 * * *";

    private static final ZoneId ZONE_ID = ZoneId.of(ZONE);

    private DigestDay() {
    }

    /** The digest day a UTC-stamped {@code generatedAt} belongs to. */
    public static LocalDate of(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(ZONE_ID)
                .minusHours(ROLLOVER_HOUR)
                .toLocalDate();
    }

    /** The digest day in effect right now. */
    public static LocalDate current(Clock clock) {
        return of(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
    }

    /** First UTC instant of {@code day} (its 05:00 KST run), as stored timestamps are compared. */
    public static LocalDateTime startUtc(LocalDate day) {
        return day.atTime(ROLLOVER_HOUR, 0)
                .atZone(ZONE_ID)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    /** First UTC instant after {@code day} — the exclusive end of its window. */
    public static LocalDateTime endUtc(LocalDate day) {
        return startUtc(day.plusDays(1));
    }
}
