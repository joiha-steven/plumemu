package com.plumemu.content

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.ScanFinish
import com.plumemu.coredata.db.SmartListDao
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.EntryKind
import com.plumemu.home.HomeContent
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Collections folders from a real in-memory library: their games, the own collections and the date change (a fake clock). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class LibraryHomeContentFoldersTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
    private var folderId = 0L
    private var now = NOW

    private fun source(dayChanges: kotlinx.coroutines.flow.Flow<Long>) =
        LibraryHomeContent(
            db.gameDao(),
            { LibraryHomeContentTest.catalog },
            MutableStateFlow(emptyList()),
            MutableStateFlow(true),
            Dispatchers.Default,
            database = db,
            clock = { now },
            dayChanges = dayChanges,
        )

    @Before
    fun setUp() {
        runBlocking {
            folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/roms", displayName = "ROMs", addedAt = 0))
            db.scanDao().applyBatch(
                ScanBatch(
                    folderId,
                    systems = listOf(SystemEntity("snes", "snes")),
                    newGames = listOf(game("zelda.sfc", "Zelda"), game("actraiser.sfc", "ActRaiser")),
                    addedAt = NOW - 1_000,
                ),
            )
            db.scanDao().finishScan(ScanFinish(folderId))
        }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun HomeContent.folder(id: String) = folderEntries.first { it.id == id }

    private suspend fun contentWhere(source: LibraryHomeContent, check: (HomeContent) -> Boolean): HomeContent = withTimeout(TIMEOUT_MS) { source.content.first(check) }

    @Test
    fun theFoldersCarryTheirGamesAndEveryFolderIsListedEmptyOnesToo() = runBlocking {
        val id = db.gameDao().observeSystemGames("snes").first().first { it.title == "Zelda" }.id
        val own = db.collectionDao().createWith("RPG", id, NOW)!!
        val content = contentWhere(source(MutableSharedFlow<Long>(replay = 1).also { it.tryEmit(NOW) })) { c -> c.folderEntries.any { it.id == "collection:$own" } && c.folderEntries.first { it.id == "recently_added" }.count == 2 }
        assertEquals(listOf("recent", "most_played", "recently_added", "collection:$own"), content.folderEntries.map { it.id })
        assertEquals("Recent folder keeps Recent's kind", EntryKind.Recent, content.folder("recent").kind)
        assertEquals(listOf("Zelda"), content.folder("collection:$own").rows.map { it.title })
        assertEquals("RPG", content.folder("collection:$own").name)
        assertEquals("Most played is listed though empty", 0, content.folder("most_played").count)
        val list = content.entries.first { it.kind == EntryKind.Collections }
        assertEquals("every folder row then New collection…", listOf("recent", "most_played", "recently_added", "collection:$own", HomeContent.NEW_COLLECTION_ID), list.rows.map { it.id })
        assertEquals("an empty built-in shows no count; New collection… none", listOf("", "", "2", "1", ""), list.rows.map { it.region })
    }

    @Test
    fun recentlyAddedIsAskedAgainWhenTheDateChanges() = runBlocking {
        val days = MutableSharedFlow<Long>(replay = 1).also { it.tryEmit(NOW) }
        val source = source(days)
        val first = contentWhere(source) { it.folderEntries.first { f -> f.id == "recently_added" }.count == 2 }
        assertEquals(2, first.folderEntries.first { it.id == "recently_added" }.count)
        // A month and a day later, with the clock moved to match: the games are no longer new, with no change in the database.
        now = NOW + SmartListDao.RECENTLY_ADDED_MS + DAY_MS
        days.emit(now)
        val later = contentWhere(source) { it.folderEntries.first { f -> f.id == "recently_added" }.count == 0 }
        assertEquals("emptied: no bare 0 on its row", "", later.entries.first { it.kind == EntryKind.Collections }.rows.first { it.id == "recently_added" }.region)
    }

    @Test
    fun theDayTickFollowsAFakeClockToTheNextLocalMidnight() = runTest {
        val start = 1_800_000_000_000L // 08:00 UTC of some day
        val ticks = LibraryHomeContent.dayStarts({ start + testScheduler.currentTime }, { TimeZone.getTimeZone("UTC") }).take(3).toList()
        assertEquals(start, ticks[0])
        val midnight = (start / DAY_MS + 1) * DAY_MS
        assertEquals("the second tick is the next midnight", midnight, ticks[1])
        assertEquals("and the third the one after", midnight + DAY_MS, ticks[2])
        assertTrue(ticks[1] > ticks[0])
    }

    @Test
    fun aClockJumpPastMidnightIsSeenAtTheNextWakeAndByAFreshCollection() = runTest {
        var clockNow = 1_800_000_000_000L
        val utc = { TimeZone.getTimeZone("UTC") }
        val seen = mutableListOf<Long>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { LibraryHomeContent.dayStarts({ clockNow }, utc).toList(seen) }
        assertEquals("the first reading", listOf(clockNow), seen)
        // The date moves two days on (a sleep, a manual change) while no wait has run out.
        val jumped = clockNow + 2 * DAY_MS
        clockNow = jumped
        assertEquals("nothing is told before a wake-up", 1, seen.size)
        // Resume: the screen collects again and reads the clock at once.
        assertEquals(jumped, LibraryHomeContent.dayStarts({ clockNow }, utc).first())
        // Or the running collection wakes within the hour and tells the new day.
        testScheduler.advanceTimeBy(3_600_001L)
        assertEquals(listOf(1_800_000_000_000L, jumped), seen)
        job.cancel()
    }

    @Test
    fun aTimeZoneChangeMovesMidnightWithoutANewCollection() = runTest {
        val start = 1_800_000_000_000L // 08:00 UTC
        var zone = TimeZone.getTimeZone("UTC")
        val seen = mutableListOf<Long>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { LibraryHomeContent.dayStarts({ start + testScheduler.currentTime }, { zone }).toList(seen) }
        zone = TimeZone.getTimeZone("Etc/GMT+12") // UTC-12: still the day before there
        testScheduler.advanceTimeBy(3_600_001L)
        assertEquals("the new zone's date is told within the hour", 2, seen.size)
        job.cancel()
    }

    private fun game(path: String, title: String) = GameEntity(
        folderId = folderId,
        documentId = "root/snes/$path",
        parentDocumentId = "root/snes",
        relativePath = "snes/$path",
        fileName = path,
        sizeBytes = 1,
        modifiedAt = 1,
        title = title,
        sortName = title.lowercase(),
        regions = "USA",
        discGroup = null,
        discCount = 1,
        systemId = "snes",
        isPrimaryCopy = true,
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val DAY_MS = 86_400_000L
        const val TIMEOUT_MS = 10_000L
    }
}
