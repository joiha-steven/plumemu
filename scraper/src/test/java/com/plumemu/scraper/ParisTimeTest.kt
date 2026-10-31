package com.plumemu.scraper

import com.plumemu.scraper.gate.ParisTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ParisTimeTest {
    private val hour = 60L * 60 * 1000

    @Test fun `summer time midnight is 22_00 UTC`() {
        assertEquals(utc(2026, 7, 2, 22, 0), ParisTime.nextMidnight(utc(2026, 7, 2, 9, 30)))
    }

    @Test fun `winter time midnight is 23_00 UTC`() {
        assertEquals(utc(2026, 1, 15, 23, 0), ParisTime.nextMidnight(utc(2026, 1, 15, 9, 30)))
    }

    @Test fun `exactly midnight waits for the next one`() {
        val midnight = utc(2026, 1, 15, 23, 0)
        assertEquals(midnight + 24 * hour, ParisTime.nextMidnight(midnight))
    }

    @Test fun `on the spring-forward night the day is 23 hours`() {
        // Paris jumps from 02:00 to 03:00 on the last Sunday of March (here the 29th). 01:30 CET is 00:30 UTC.
        val now = utc(2026, 3, 29, 0, 30)
        val next = ParisTime.nextMidnight(now)
        // The next midnight is in summer time: the 30th at 00:00 CEST = the 29th at 22:00 UTC.
        assertEquals(utc(2026, 3, 29, 22, 0), next)
        assertEquals(utc(2026, 3, 29, 22, 0), ParisTime.nextMidnight(utc(2026, 3, 28, 23, 30)))
    }

    @Test fun `on the fall-back night the day is 25 hours`() {
        // Paris goes back from 03:00 to 02:00 on the last Sunday of October (here the 25th). 02:30 happens twice; take the
        // first, in summer time: 00:30 UTC.
        val now = utc(2026, 10, 25, 0, 30)
        // The next midnight is in winter time: the 26th at 00:00 CET = the 25th at 23:00 UTC.
        assertEquals(utc(2026, 10, 25, 23, 0), ParisTime.nextMidnight(now))
        // The second 02:30, in winter time, is 01:30 UTC: same midnight.
        assertEquals(utc(2026, 10, 25, 23, 0), ParisTime.nextMidnight(utc(2026, 10, 25, 1, 30)))
        // Just before that midnight (22:59 UTC is 23:59 CET) it is still that midnight.
        assertEquals(utc(2026, 10, 25, 23, 0), ParisTime.nextMidnight(utc(2026, 10, 25, 22, 59)))
    }
}
