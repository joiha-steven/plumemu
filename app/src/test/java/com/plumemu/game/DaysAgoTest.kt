package com.plumemu.game

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/** [daysAgo]: whole calendar days in a zone, around midnight and across a daylight-saving change. */
class DaysAgoTest {
    private val utc = TimeZone.getTimeZone("UTC")

    /** [text] is local time in [zoneId] ("Z" suffix means UTC). */
    private fun at(
        text: String,
        zoneId: String = "UTC",
    ) = LocalDateTime.parse(text.removeSuffix("Z")).atZone(ZoneId.of(zoneId)).toInstant().toEpochMilli()

    @Test
    fun aMinuteBeforeMidnightIsYesterdayAndAMinuteAfterIsToday() {
        val now = at("2026-10-21T00:01:00Z")
        assertEquals(1, daysAgo(at("2026-10-20T23:59:00Z"), now, utc))
        assertEquals(0, daysAgo(at("2026-10-21T00:00:00Z"), now, utc))
    }

    @Test
    fun theZoneDecidesWhereTheDayEnds() {
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
        val now = at("2026-10-21T12:00:00Z")
        val play = at("2026-10-20T23:30:00Z")
        assertEquals("08:30 on the 21st in Tokyo", 0, daysAgo(play, now, tokyo))
        assertEquals("still the 20th in UTC", 1, daysAgo(play, now, utc))
    }

    @Test
    fun aDaylightSavingChangeDoesNotMakeADayShortOrLong() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        // Clocks go back on 25 October 2026 (a 25-hour day) and forward on 29 March 2026 (23 hours).
        assertEquals(1, daysAgo(at("2026-10-25T00:30:00", "Europe/Berlin"), at("2026-10-26T00:30:00", "Europe/Berlin"), berlin))
        assertEquals(1, daysAgo(at("2026-10-25T23:30:00", "Europe/Berlin"), at("2026-10-26T00:10:00", "Europe/Berlin"), berlin))
        assertEquals(1, daysAgo(at("2026-03-29T23:30:00", "Europe/Berlin"), at("2026-03-30T00:10:00", "Europe/Berlin"), berlin))
        assertEquals(2, daysAgo(at("2026-03-28T12:00:00", "Europe/Berlin"), at("2026-03-30T12:00:00", "Europe/Berlin"), berlin))
    }

    @Test
    fun aClockSetBackReadsAsToday() {
        assertEquals(0, daysAgo(at("2026-10-22T10:00:00Z"), at("2026-10-21T10:00:00Z"), utc))
    }
}
