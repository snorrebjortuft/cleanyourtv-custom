package com.gathin.tvhome;

import org.junit.Test;
import java.time.LocalDate;
import java.time.MonthDay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CountdownTest {
    private static final MonthDay XMAS_EVE = MonthDay.of(12, 24);

    @Test public void countsDaysToTheDateThisYear() {
        assertEquals(25, MainActivity.daysUntil(XMAS_EVE, LocalDate.of(2026, 11, 29)));
        assertEquals(1, MainActivity.daysUntil(XMAS_EVE, LocalDate.of(2026, 12, 23)));
    }

    @Test public void theDayItselfIsZero() {
        assertEquals(0, MainActivity.daysUntil(XMAS_EVE, LocalDate.of(2026, 12, 24)));
    }

    @Test public void afterTheDateItCountsToNextYear() {
        assertEquals(364, MainActivity.daysUntil(XMAS_EVE, LocalDate.of(2026, 12, 25)));
        assertEquals(357, MainActivity.daysUntil(XMAS_EVE, LocalDate.of(2027, 1, 1)));
    }

    @Test public void parsesMonthDayAndRejectsTheRest() {
        assertEquals(XMAS_EVE, MainActivity.parseCountdownDate("12-24"));
        assertNull(MainActivity.parseCountdownDate(""));
        assertNull(MainActivity.parseCountdownDate("24.12"));
        assertNull(MainActivity.parseCountdownDate("13-01"));
    }
}
