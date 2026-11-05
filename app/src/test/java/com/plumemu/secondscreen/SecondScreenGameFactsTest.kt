package com.plumemu.secondscreen

import com.plumemu.coredata.db.PlaySessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** The game card's play tile: sessions summed per week over the last 8 weeks, weeks as Statistics counts them. */
class SecondScreenGameFactsTest {
    private val zone = TimeZone.getTimeZone("UTC")
    private val locale = Locale.UK // weeks start on Monday

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
    ): Long = Calendar.getInstance(zone, locale).apply { clear(); set(year, month - 1, day, hour, 0) }.timeInMillis

    private fun session(
        startedAt: Long,
        seconds: Long,
    ) = PlaySessionEntity(gameId = 1, systemId = "snes", startedAt = startedAt, endedAt = startedAt + seconds * 1000, seconds = seconds)

    // A Wednesday (4 November); its week starts on the Monday before.
    private val now = at(2026, 11, 4)

    @Test
    fun sessionsAreSummedPerWeekOldestFirstAndTheLastIsThisWeek() {
        val weeks =
            playWeeksOf(
                listOf(session(at(2026, 11, 2, 0), 600), session(at(2026, 11, 4, 9), 300), session(at(2026, 10, 27), 1_200), session(at(2026, 9, 14), 60)),
                now,
                zone,
                locale,
            )
        assertEquals(8, weeks.weekSeconds.size)
        assertEquals(900L, weeks.weekSeconds[7])
        assertEquals(1_200L, weeks.weekSeconds[6])
        // Monday 14 September starts the eighth week back (index 0).
        assertEquals(60L, weeks.weekSeconds[0])
        assertEquals(4, weeks.sessions)
        assertEquals(2_160L, weeks.totalSeconds)
    }

    @Test
    fun aSessionBeforeTheFirstWeekOrAfterTheCurrentOneIsLeftOut() {
        val weeks = playWeeksOf(listOf(session(at(2026, 9, 13, 23), 500), session(at(2026, 11, 9, 1), 500)), now, zone, locale)
        assertEquals(0, weeks.sessions)
        assertEquals(0L, weeks.totalSeconds)
        assertNull(weeks.averageMinutes)
    }

    @Test
    fun theAverageSessionIsInWholeMinutesAtLeastOne() {
        assertEquals(15, PlayWeeks(List(8) { 0L }, sessions = 9, totalSeconds = 8_400).averageMinutes)
        assertEquals(1, PlayWeeks(List(8) { 0L }, sessions = 3, totalSeconds = 30).averageMinutes)
    }
}
