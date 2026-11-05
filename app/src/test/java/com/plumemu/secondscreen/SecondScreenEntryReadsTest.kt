package com.plumemu.secondscreen

import com.plumemu.coredata.db.RaAchievementEntity
import com.plumemu.coredata.db.RaMatchEntity
import com.plumemu.coredata.db.RaProgressEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.GraphStatisticsFixture
import com.plumemu.graph.GraphSystemFigures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The system column cards' figures read from an in-memory library: played, never played, signed in or out, empty. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
internal class SecondScreenEntryReadsTest : GraphStatisticsFixture() {
    private var isRaSignedIn = false

    // A Wednesday noon, a week starting on Sunday (US): the Monday is index 1, today index 3.
    private val today = at("2026-11-04T12:00:00Z")

    private fun reads(): SecondScreenEntryReads {
        clock = today
        return SecondScreenEntryReads(
            figures = GraphSystemFigures({ db.gameDao() }, { db.playDao() }, { db.leftOutFileDao() }, Dispatchers.IO, now = { clock }, zone = { zone }, locale = { locale }),
            games = { db.gameDao() },
            plays = { db.playDao() },
            ra = { db.raDao() },
            isRaSignedIn = { isRaSignedIn },
            emulatorOf = { null },
            artOf = { keys -> art.observe(keys).first() },
            now = { clock },
            zone = { zone },
            locale = { locale },
        )
    }

    private fun system(
        id: String = "snes",
        count: Int,
        hasBoxArt: Boolean = false,
    ) = SystemEntry(id, "Super Nintendo", count, hasBoxArt = hasBoxArt)

    private suspend fun filled(content: EntryContent) = reads().fill(content)

    @Test
    fun aPlayedSystemHasItsCoversPlayTimeAndPlayedCount() =
        runBlocking {
            val ids = library(3)
            db.playDao().addSession(ids[0], endedAt = today - 2 * DAY, seconds = 600)
            db.playDao().addSession(ids[1], endedAt = today - HOUR, seconds = 300)
            val figures = (filled(system(count = 3)) as SystemEntry).figures!!
            assertEquals(listOf("Game 00001", "Game 00000"), figures.lastPlayed.map { it.title })
            assertEquals(today - HOUR, figures.lastPlayed.first().playedAt)
            assertEquals(900L, figures.playSeconds)
            assertEquals(2, figures.playedGames)
            assertNull("signed out: no achievements tile", figures.achievements)
            assertNull("no emulator is read here", figures.emulator)
            assertEquals(today, figures.now)
        }

    @Test
    fun aSystemNeverPlayedHasZerosAndNoCovers() =
        runBlocking {
            library(2)
            val figures = (filled(system(count = 2)) as SystemEntry).figures!!
            assertEquals(emptyList<EntryGame>(), figures.lastPlayed)
            assertEquals(listOf(0L, 0L), listOf(figures.playSeconds, figures.playedGames.toLong()))
        }

    @Test
    fun anEmptySystemReadsWithoutFailing() =
        runBlocking {
            val figures = (filled(system(id = "ps2", count = 0)) as SystemEntry).figures!!
            assertEquals(emptyList<EntryGame>(), figures.lastPlayed)
            assertEquals(0, figures.playedGames)
            assertEquals(0, figures.leftOutFiles)
        }

    @Test
    fun achievementsComeOnlyWhileSignedInAndWithProgress() =
        runBlocking {
            val ids = library(2)
            db.raDao().saveMatch(RaMatchEntity(ids[0], RaMatchEntity.MATCHED, 11, "0123456789abcdef0123456789abcdef", null, "Game 00000", 1, 1, 5))
            db.raDao().saveGameProgress(RaProgressEntity(11, 3, 3, 0, "mastered", 7, pointsEarned = 40, pointsTotal = 40), listOf(RaAchievementEntity(11, 1, "A", "b", 5, 100, 100)))
            isRaSignedIn = false
            assertNull((filled(system(count = 2)) as SystemEntry).figures!!.achievements)
            isRaSignedIn = true
            val ra = (filled(system(count = 2)) as SystemEntry).figures!!.achievements!!
            assertEquals(1, ra.mastered)
            assertEquals(1, ra.gamesWithProgress)
            assertEquals(40L, ra.points)
        }

    @Test
    fun signedInWithNoProgressOnTheSystemLeavesTheTileOut() =
        runBlocking {
            library(2)
            isRaSignedIn = true
            assertNull((filled(system(count = 2)) as SystemEntry).figures!!.achievements)
        }

    @Test
    fun aCardWithFiguresIsNotReadAgain() =
        runBlocking {
            library(1)
            val once = filled(system(count = 1)) as SystemEntry
            assertNull(reads().fill(once))
        }

    @Test
    fun recentHasItsGamesAndTheWeeksDays() =
        runBlocking {
            val ids = library(3)
            db.playDao().addSession(ids[0], endedAt = at("2026-11-02T12:00:00Z"), seconds = 600)
            db.playDao().addSession(ids[1], endedAt = today, seconds = 300)
            db.playDao().addSession(ids[2], endedAt = at("2026-10-28T12:00:00Z"), seconds = 1200)
            val figures = (filled(RecentEntry("Recent", 3)) as RecentEntry).figures!!
            assertEquals(listOf("Game 00001", "Game 00000", "Game 00002"), figures.games.map { it.title })
            assertEquals(listOf(0L, 600L, 0L, 300L, 0L, 0L, 0L), figures.week.daySeconds)
            assertEquals(3, figures.week.todayIndex)
            assertEquals(2, figures.week.games)
            assertEquals(1200L, figures.week.lastWeekSeconds)
            assertEquals(900L, figures.week.totalSeconds)
        }

    @Test
    fun aRecentWithNothingPlayedThisWeekHasEmptyDays() =
        runBlocking {
            val ids = library(1)
            db.playDao().addSession(ids[0], endedAt = at("2026-10-20T12:00:00Z"), seconds = 600)
            val figures = (filled(RecentEntry("Recent", 1)) as RecentEntry).figures!!
            assertEquals(List(7) { 0L }, figures.week.daySeconds)
            assertEquals(0L, figures.week.totalSeconds)
            assertEquals(1, figures.games.size)
        }

    @Test
    fun anEmptyRecentHasNoGames() =
        runBlocking {
            val figures = (filled(RecentEntry("Recent", 0)) as RecentEntry).figures!!
            assertEquals(emptyList<EntryGame>(), figures.games)
            assertEquals(0L, figures.week.totalSeconds)
        }

    @Test
    fun favoritesGetTheirPicturesAndAreNotReadTwice() =
        runBlocking {
            val one = EntryGame(1, "A", "snes", "game:1")
            val two = EntryGame(2, "B", "snes", "game:2")
            keysWithoutArt += "game:2"
            val card = FavoritesEntry("Favorites", 2, 1, listOf(one, two), hasBoxArt = true)
            assertEquals(false, card.isArtRead)
            val read = filled(card) as FavoritesEntry
            assertTrue(read.isArtRead)
            assertEquals("the saved box front", 300, read.games[0].art?.width)
            assertNull("no result: the poster", read.games[1].art)
            assertNull(reads().fill(read))
        }

    @Test
    fun favoritesWithoutBoxArtAskForNothing() =
        runBlocking {
            val card = FavoritesEntry("Favorites", 1, 1, listOf(EntryGame(1, "A", "snes", null)))
            assertTrue(card.isArtRead)
            assertNull(filled(card))
            assertTrue(asked.isEmpty())
        }
}
