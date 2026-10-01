package com.curio.shared.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DigestDayTest {

    @Test
    void aDigestDayRollsOverAt0500Seoul_whichIs2000UtcTheDayBefore() {
        assertThat(DigestDay.of(LocalDateTime.of(2026, 9, 16, 19, 59))).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(DigestDay.of(LocalDateTime.of(2026, 9, 16, 20, 0))).isEqualTo(LocalDate.of(2026, 9, 17));
    }

    @Test
    void utcMidnight_isMidDayInSeoul_andDoesNotStartANewDigestDay() {
        assertThat(DigestDay.of(LocalDateTime.of(2026, 9, 16, 23, 59)))
                .isEqualTo(DigestDay.of(LocalDateTime.of(2026, 9, 17, 0, 0)));
    }

    @Test
    void windowIsExactly24Hours_inUtc() {
        LocalDate day = LocalDate.of(2026, 9, 17);
        assertThat(DigestDay.startUtc(day)).isEqualTo(LocalDateTime.of(2026, 9, 16, 20, 0));
        assertThat(DigestDay.endUtc(day)).isEqualTo(LocalDateTime.of(2026, 9, 17, 20, 0));
        assertThat(DigestDay.of(DigestDay.startUtc(day))).isEqualTo(day);
        assertThat(DigestDay.of(DigestDay.endUtc(day).minusNanos(1))).isEqualTo(day);
    }

    @Test
    void currentFollowsTheClock() {
        Clock at0459Kst = Clock.fixed(Instant.parse("2026-09-16T19:59:00Z"), ZoneOffset.UTC);
        Clock at0500Kst = Clock.fixed(Instant.parse("2026-09-16T20:00:00Z"), ZoneOffset.UTC);
        assertThat(DigestDay.current(at0459Kst)).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(DigestDay.current(at0500Kst)).isEqualTo(LocalDate.of(2026, 9, 17));
    }
}
