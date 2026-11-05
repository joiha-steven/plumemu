package com.plumemu.graph

import com.plumemu.coredata.db.LeftOutFileEntity
import com.plumemu.coredata.db.LeftOutWrite
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.ScanFinish
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.plumemu.settings.screen.StatisticsState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The system and Recent figures through the graph accessor, on the in-memory library of the Statistics tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
internal class GraphSystemFiguresTest : GraphStatisticsFixture() {
    private fun figures() = GraphSystemFigures({ db.gameDao() }, { db.playDao() }, { db.leftOutFileDao() }, Dispatchers.IO, now = { clock }, zone = { zone }, locale = { locale }, arcadeSystems = listOf("arcade"))

    @Test
    fun anEmptyLibraryHasNoRowsAndZeroFigures() =
        runBlocking {
            val f = figures()
            assertTrue(f.lastPlayed("snes", 6).first().isEmpty())
            assertTrue(f.recent(5).first().isEmpty())
            assertEquals(listOf(0L, 0L, 0L, 0L), listOf(f.playSeconds("snes"), f.playedGames("snes").toLong(), f.leftOutFiles("snes").toLong(), f.gamesPlayedThisWeek().toLong()))
        }

    @Test
    fun aLibraryNobodyPlayedHasGamesButNoPlayFigures() =
        runBlocking {
            library(3)
            val f = figures()
            assertEquals(3, db.gameDao().observeSystemsWithGames().first().single().gameCount)
            assertTrue(f.lastPlayed("snes", 6).first().isEmpty())
            assertEquals(0L, f.playSeconds("snes"))
            assertEquals(0, f.playedGames("snes"))
        }

    @Test
    fun severalSystemsKeepTheirFiguresApartAndTheSixtySecondEdgeIsInclusive() =
        runBlocking {
            val ids = library(4) { listOf("snes", "snes", "gba", "snes")[it] }
            db.playDao().addSession(ids[0], endedAt = 1_000, seconds = 60)
            db.playDao().addSession(ids[1], endedAt = 2_000, seconds = 59)
            db.playDao().addSession(ids[2], endedAt = 3_000, seconds = 600)
            db.playDao().addSession(ids[3], endedAt = 4_000, seconds = 100)
            db.gameDao().setHidden(ids[3], true)
            val f = figures()
            assertEquals(219L, f.playSeconds("snes"))
            assertEquals(600L, f.playSeconds("gba"))
            assertEquals("60 s counts, 59 s and the hidden game do not", 1, f.playedGames("snes"))
            assertEquals(mapOf("snes" to 1, "gba" to 1), f.playedGamesBySystem())
            assertEquals(1, f.playedGames("gba"))
            assertEquals(listOf(2_000L, 1_000L), f.lastPlayed("snes", 6).first().map { it.lastPlayedAt })
            assertEquals("limited", listOf(2_000L), f.lastPlayed("snes", 1).first().map { it.lastPlayedAt })
        }

    @Test
    fun leftOutFilesCountPerSystemWithTheMoreRowsAndNoneForSystemlessOnes() =
        runBlocking {
            library(1)
            val folder = 1L // the only picked folder of a fresh database
            fun row(system: String?, path: String?, more: Int = 0) = LeftOutFileEntity(folderId = folder, directoryPath = "d${path ?: "more"}$system", relativePath = path, reason = "junk", systemId = system, more = more)
            val rows = listOf(row("snes", "a"), row("snes", null, more = 4), row(null, "x"))
            db.scanDao().finishScan(ScanFinish(folderId = folder, leftOut = LeftOutWrite(false, rows.map { it.directoryPath }, rows)))
            val f = figures()
            assertEquals(5, f.leftOutFiles("snes"))
            assertEquals(0, f.leftOutFiles("gba"))
        }

    @Test
    fun gamesPlayedThisWeekAreDistinctAndStopAtTheWeekStart() =
        runBlocking {
            val ids = library(3)
            val day = 86_400_000L
            clock = 20 * day + 1_000
            val weekStart = weekStartOf(clock, zone, locale)
            db.playDao().addSession(ids[0], endedAt = weekStart + 10_000, seconds = 5)
            db.playDao().addSession(ids[0], endedAt = weekStart + 20_000, seconds = 5)
            db.playDao().addSession(ids[1], endedAt = weekStart + 30_000, seconds = 5)
            db.playDao().addSession(ids[2], endedAt = weekStart - 5 * day, seconds = 5)
            assertEquals(2, figures().gamesPlayedThisWeek())
        }

    /** The regional copies of one game in one directory, the first one the copy the lists show. */
    private suspend fun copies(vararg fileNames: String): List<Long> {
        val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/copies", displayName = "ROMs", addedAt = 0))
        val rows = fileNames.mapIndexed { at, file -> game(folderId, "snes", file).copy(fileName = file, title = "Zelda", sortName = "zelda", fileSortName = "zelda", isPrimaryCopy = at == 0) }
        db.scanDao().applyBatch(ScanBatch(folderId, systems = listOf(SystemEntity("snes", "snes")), newGames = rows))
        val byFile = db.scanDao().gamesInFolder(folderId).associate { it.fileName to it.id }
        return fileNames.map { byFile.getValue(it) }
    }

    @Test
    fun aGameWithCopiesCountsOnceWhateverCopyWasPlayedAndNeverMoreThanTheSystemHolds() =
        runBlocking {
            val (usa, europe) = copies("Zelda (USA).sfc", "Zelda (Europe).sfc")
            db.playDao().addSession(europe, endedAt = 1, seconds = 120)
            val f = figures()
            assertEquals("played only on the non-primary copy: once", 1, f.playedGames("snes"))
            db.playDao().addSession(usa, endedAt = 2, seconds = 120)
            assertEquals("played on both copies: still once", 1, f.playedGames("snes"))
            assertEquals("equals Statistics' own count", 1, (reader().read() as StatisticsState.Read).statistics.playedGames)
            assertTrue(f.playedGames("snes") <= db.gameDao().observeSystemsWithGames().first().single().gameCount)
        }

    @Test
    fun twoCopiesOf30SecondsMakeOnePlayedGameAndOneOf29PlusNothingMakesNone() =
        runBlocking {
            val (usa, europe) = copies("Zelda (USA).sfc", "Zelda (Europe).sfc")
            db.playDao().addSession(usa, endedAt = 1, seconds = 30)
            val f = figures()
            assertEquals("30 s is not a minute", 0, f.playedGames("snes"))
            db.playDao().addSession(europe, endedAt = 2, seconds = 30)
            assertEquals("30 + 30 s over two copies is one played game", 1, f.playedGames("snes"))
            assertEquals(mapOf("snes" to 1), f.playedGamesBySystem())
        }

    /** An arcade parent/clone pair (other file names) counts as one game, as in Statistics, once the same grouping is passed in (AppGraph passes `arcadeGamesOf`). */
    @Test
    fun anArcadeParentAndCloneCountOnceLikeStatisticsDoes() =
        runBlocking {
            val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/arcade", displayName = "ROMs", addedAt = 0))
            fun set(name: String, shown: Boolean) = game(folderId, "arcade", name).copy(fileName = "$name.zip", title = "Cadillacs and Dinosaurs", fileSortName = name, isPrimaryCopy = shown)
            db.scanDao().applyBatch(ScanBatch(folderId, systems = listOf(SystemEntity("arcade", "Arcade")), newGames = listOf(set("dinou", true), set("dino", false), set("pang", true))))
            val byFile = db.scanDao().gamesInFolder(folderId).associate { it.fileName to it.id }
            db.playDao().addSession(byFile.getValue("dino.zip"), endedAt = 1, seconds = 30)
            db.playDao().addSession(byFile.getValue("dinou.zip"), endedAt = 2, seconds = 30)
            val games =
                object : ArcadeGames {
                    override fun gameOf(systemId: String, fileName: String) = if (fileName.startsWith("dino")) "dino" else fileName.substringBefore('.')

                    override fun parentOf(systemId: String, fileName: String) = if (fileName == "dinou.zip") "dino" else null
                }
            val f = GraphSystemFigures({ db.gameDao() }, { db.playDao() }, { db.leftOutFileDao() }, Dispatchers.IO, arcadeSystems = listOf("arcade"), arcadeGames = games)
            assertEquals("30 + 30 s over the pair is one played game", 1, f.playedGames("arcade"))
            assertEquals((reader(arcadeGames = games).read() as StatisticsState.Read).statistics.playedGames, f.playedGames("arcade"))
            assertEquals("without the grouping the clone is apart and neither reaches a minute", 0, figures().playedGames("arcade"))
        }

    @Test
    fun aGameShowsOnceInLastPlayedAsItsShownCopyWithTheLatestPlayOfAnyCopy() =
        runBlocking {
            val (usa, europe) = copies("Zelda (USA).sfc", "Zelda (Europe).sfc")
            val f = figures()
            db.playDao().addSession(europe, endedAt = 500, seconds = 90)
            val onlyOther = f.lastPlayed("snes", 6).first()
            assertEquals("played only on the non-primary copy: listed as the shown one", listOf(usa to 500L), onlyOther.map { it.id to it.lastPlayedAt })
            db.playDao().addSession(usa, endedAt = 300, seconds = 90)
            assertEquals("both copies: once, the latest play", listOf(usa to 500L), f.lastPlayed("snes", 6).first().map { it.id to it.lastPlayedAt })
        }

    @Test
    fun lastPlayedLeavesHiddenGamesSortsNewestFirstAndKeepsOnesRemovedFromRecent() =
        runBlocking {
            val ids = library(3)
            ids.forEachIndexed { i, id -> db.playDao().addSession(id, endedAt = (i + 1) * 100L, seconds = 10) }
            db.gameDao().setHidden(ids[2], true)
            db.playDao().removeFromRecent(ids[1])
            assertEquals(listOf(ids[1] to 200L, ids[0] to 100L), figures().lastPlayed("snes", 6).first().map { it.id to it.lastPlayedAt })
        }

    @Test
    fun gamesTouchedBetweenIgnoreASessionEndingExactlyAtFromAndCountTwoPlayedCopiesOnce() =
        runBlocking {
            val (usa, europe) = copies("Zelda (USA).sfc", "Zelda (Europe).sfc")
            db.playDao().addSession(usa, endedAt = 10_000, seconds = 10)
            val f = figures()
            assertEquals("ends exactly at from: no overlap", 0, f.gamesPlayedBetween(10_000, 20_000))
            assertEquals(1, f.gamesPlayedBetween(9_000, 20_000))
            db.playDao().addSession(europe, endedAt = 12_000, seconds = 5)
            assertEquals("two copies played in the range: one game", 1, f.gamesPlayedBetween(0, 20_000))
            assertEquals("5 s is below a minute but touched", 1, f.gamesPlayedBetween(11_000, 20_000))
        }

    /** Statistics' rule for hidden games: their seconds stay in play time, but they are never counted as a game. */
    @Test
    fun aHiddenGameAddsPlayTimeButIsNotAGamePlayedThisWeekLikeStatistics() =
        runBlocking {
            val ids = library(2)
            db.playDao().addSession(ids[0], endedAt = 10_000, seconds = 120)
            db.playDao().addSession(ids[1], endedAt = 11_000, seconds = 120)
            db.gameDao().setHidden(ids[1], true)
            val f = figures()
            val statistics = (reader().read() as StatisticsState.Read).statistics
            assertEquals("seconds of the hidden game stay in play time", 240L, f.playSeconds("snes"))
            assertEquals(statistics.totalSeconds, f.playSeconds("snes"))
            assertEquals("the hidden game is not a game played", 1, f.gamesPlayedBetween(0, 20_000))
            assertEquals(statistics.playedGames, f.playedGames("snes"))
            db.gameDao().setHidden(ids[1], false)
            assertEquals("shown again: counted again", 2, f.gamesPlayedBetween(0, 20_000))
        }
}
