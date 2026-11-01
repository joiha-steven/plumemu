package com.plumemu.graph

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.content.LibraryHomeContentTest
import com.plumemu.coredata.db.NewPinnedShortcut
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.ArtPicture
import com.plumemu.home.CoverImages
import com.plumemu.home.DetailsExtras
import com.plumemu.home.GameArt
import com.plumemu.home.GameDetails
import com.plumemu.home.HomeArtSource
import com.plumemu.settings.screen.PlayStatistics
import com.plumemu.settings.screen.StatisticsState
import com.plumemu.settings.screen.TopSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Statistics' week as seven days: where each day starts and ends in the device zone, and today's place. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
internal class GraphStatisticsDaysTest : GraphStatisticsFixture() {
    private fun weekOf(
        at: Long,
        zoneId: String,
        locale: Locale = Locale.UK,
    ): List<Long> = dayStartsOf(weekStartOf(at, TimeZone.getTimeZone(zoneId), locale), TimeZone.getTimeZone(zoneId))

    @Test
    fun dayStartsAreEightMidnightsAndADaylightSavingDayIsNot24Hours() {
        val normal = weekOf(at("2026-10-21T12:00:00Z"), "UTC")
        assertEquals(8, normal.size)
        assertEquals(listOf(DAY), normal.zipWithNext { a, b -> b - a }.distinct())
        assertEquals(at("2026-10-19T00:00:00Z"), normal.first())
        // Berlin: the clocks go back on Sunday 25 October 2026 (25 h) and forward on 29 March 2026 (23 h).
        val autumn = weekOf(at("2026-10-22T12:00:00Z"), "Europe/Berlin")
        assertEquals("only Sunday is 25 h", listOf(DAY, DAY, DAY, DAY, DAY), autumn.zipWithNext { x, y -> y - x }.take(5))
        assertEquals(DAY + HOUR, autumn[7] - autumn[6])
        val spring = weekOf(at("2026-03-26T12:00:00Z"), "Europe/Berlin")
        assertEquals(DAY - HOUR, spring[7] - spring[6])
        assertEquals("the week still ends at a local midnight", at("2026-04-06T00:00:00+02:00", "Europe/Berlin"), weekOf(at("2026-03-31T12:00:00Z"), "Europe/Berlin").last())
    }

    private suspend fun sessionAt(
        game: Long,
        from: Long,
        to: Long,
    ) = db.playDao().addSession(game, endedAt = to, seconds = (to - from) / 1_000, startedAt = from)

    @Test
    fun eachDayOfTheWeekCountsItsOwnPlayWithASessionAcrossMidnightSplitAcrossTwoDays() =
        runBlocking {
            val game = library(1).single()
            locale = Locale.UK
            clock = at("2026-10-21T12:00:00Z") // Wednesday
            // Monday 23:00 to Tuesday 01:00: one hour on each.
            sessionAt(game, at("2026-10-19T23:00:00Z"), at("2026-10-20T01:00:00Z"))
            // Wednesday 10:00-11:00, and a session after "now" that must not count today.
            sessionAt(game, at("2026-10-21T10:00:00Z"), at("2026-10-21T11:00:00Z"))
            val figures = (reader().read() as StatisticsState.Read).statistics
            assertEquals(listOf(3_600L, 3_600L, 3_600L, 0, 0, 0, 0), figures.weekDaySeconds)
            assertEquals(2, figures.todayIndex)
            assertEquals(figures.weekDaySeconds.sum(), figures.thisWeekSeconds)
        }

    @Test
    fun aSessionAcrossTheWeekStartCountsOnlyFromTheStartAndTodayIsClippedAtNow() =
        runBlocking {
            val game = library(1).single()
            locale = Locale.UK
            clock = at("2026-10-21T01:00:00Z")
            // Sunday 22:00 to Monday 02:00 (4 h): only the 2 h from Monday's midnight are this week's.
            sessionAt(game, at("2026-10-18T22:00:00Z"), at("2026-10-19T02:00:00Z"))
            // Wednesday 00:30 to 03:30 (3 h), of which only the first 30 minutes are before "now".
            sessionAt(game, at("2026-10-21T00:30:00Z"), at("2026-10-21T03:30:00Z"))
            val figures = (reader().read() as StatisticsState.Read).statistics
            assertEquals(listOf(7_200L, 0, 1_800L, 0, 0, 0, 0), figures.weekDaySeconds)
            assertEquals(figures.weekDaySeconds.sum(), figures.thisWeekSeconds)
        }

    @Test
    fun theDeviceZoneDecidesWhereADayEndsForTheSameSessions() =
        runBlocking {
            val game = library(1).single()
            locale = Locale.UK
            clock = at("2026-10-23T12:00:00Z") // Friday
            // 23:00-01:00 UTC across Tuesday night.
            sessionAt(game, at("2026-10-20T23:00:00Z"), at("2026-10-21T01:00:00Z"))
            zone = TimeZone.getTimeZone("UTC")
            val utc = (reader().read() as StatisticsState.Read).statistics.weekDaySeconds
            assertEquals(listOf(0L, 3_600L, 3_600L, 0, 0, 0, 0), utc)
            // New York (UTC-4): the same two hours are 19:00-21:00 on Tuesday.
            zone = TimeZone.getTimeZone("America/New_York")
            val newYork = (reader().read() as StatisticsState.Read).statistics.weekDaySeconds
            assertEquals(listOf(0L, 7_200L, 0, 0, 0, 0, 0), newYork)
            // Ho Chi Minh (UTC+7): 06:00-08:00 on Wednesday.
            zone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            val hanoi = (reader().read() as StatisticsState.Read).statistics.weekDaySeconds
            assertEquals(listOf(0L, 0, 7_200L, 0, 0, 0, 0), hanoi)
        }

    @Test
    fun aDaylightSavingWeekHasA25HourDayAndStillSumsToTheWeek() =
        runBlocking {
            val game = library(1).single()
            zone = TimeZone.getTimeZone("Europe/Berlin")
            // en-US: the week starts Sunday 25 October, the day the clocks go back (25 h long).
            locale = Locale.US
            clock = at("2026-10-27T12:00:00Z")
            sessionAt(game, at("2026-10-25T00:00:00+02:00", "Europe/Berlin"), at("2026-10-26T00:00:00+01:00", "Europe/Berlin"))
            val figures = (reader().read() as StatisticsState.Read).statistics
            assertEquals(25 * 3_600L, figures.weekDaySeconds[0])
            assertEquals(2, figures.todayIndex)
            assertEquals(figures.weekDaySeconds.sum(), figures.thisWeekSeconds)
        }

    @Test
    fun todayIndexFollowsTheLocalesFirstDay() =
        runBlocking {
            val game = library(1).single()
            clock = at("2026-10-18T12:00:00Z") // Sunday
            sessionAt(game, at("2026-10-18T10:00:00Z"), at("2026-10-18T11:00:00Z"))
            locale = Locale.US
            val us = (reader().read() as StatisticsState.Read).statistics
            assertEquals("en-US weeks start on Sunday", 0, us.todayIndex)
            assertEquals(listOf(3_600L, 0, 0, 0, 0, 0, 0), us.weekDaySeconds)
            locale = Locale.UK
            val uk = (reader().read() as StatisticsState.Read).statistics
            assertEquals("en-GB weeks start on Monday", 6, uk.todayIndex)
            assertEquals(listOf(0L, 0, 0, 0, 0, 0, 3_600L), uk.weekDaySeconds)
        }

    @Test
    fun withoutSessionsThereAreNoDayFigures() =
        runBlocking {
            library(1)
            clock = at("2026-10-21T12:00:00Z")
            val figures = (reader().read() as StatisticsState.Read).statistics
            assertEquals(emptyList<Long>(), figures.weekDaySeconds)
            assertEquals(0, figures.todayIndex)
        }
}
