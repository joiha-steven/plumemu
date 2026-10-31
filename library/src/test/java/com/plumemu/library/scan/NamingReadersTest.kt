package com.plumemu.library.scan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The naming pass opens several files at once ([IdentifyPass], [NAMING_READERS]): the answers,
 * the database and the progress must be exactly those of one reader, cancelling stops the readers,
 * and one failing file stops none of the others. More files than a page (200), so the pass runs
 * several pages.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NamingReadersTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val fixtures = ArrayList<ScanFixture>()

    @Before
    fun setUp() = ShadowLog.clear()

    @After
    fun tearDown() = fixtures.forEach { it.close() }

    /** A library of [FILES] games: GBA headers, zipped SNES ROMs the pack lists by CRC, names, unknown files, arcade sets. */
    private fun library(name: String): ScanFixture {
        val fx = ScanFixture(temp.newFolder(name), copyFixtures = false).also { fixtures += it }
        for (i in 0 until FILES) {
            val (path, bytes) = when (i % 5) {
                0 -> "gba/Zork Quest 2 $i (USA).gba" to TestNaming.gbaRom(size = 4096)
                1 -> "snes/zq $i.zip" to TestNaming.zip("zq.sfc", TestNaming.ZIPPED_ROM)
                2 -> "snes/Moonlit Harbour $i (Japan).sfc" to ByteArray(64) { (it + i).toByte() }
                3 -> "snes/Unknown Thing $i (Europe).sfc" to ByteArray(64) { (it * i).toByte() }
                else -> "arcade/zorkfght$i.zip" to TestNaming.zip("zf.rom", ByteArray(16))
            }
            File(fx.roms, path).apply { parentFile?.mkdirs(); writeBytes(bytes); setLastModified(FILE_TIME) }
        }
        fx.roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(ScanFixture.BASE_TIME) }
        return fx
    }

    /** What the pass decides for a row, by file name: everything it writes. */
    private suspend fun ScanFixture.namesByFile(): Map<String, List<Any?>> = games().associate {
        it.fileName to listOf(it.title, it.sortName, it.regions, it.nameTrust, it.packVersion, it.fingerprint, it.needsIdentify, it.isPrimaryCopy)
    }

    private fun failing(fx: ScanFixture) {
        // Opens, but every read fails: an SD card pulled mid-read.
        fx.tree.failingReads += "fs:gba/Zork Quest 2 5 (USA).gba"
        fx.tree.failingReads += "fs:snes/Unknown Thing 213 (Europe).sfc"
    }

    @Test
    fun severalReadersNameExactlyAsOneReaderDoes() = runBlocking {
        val one = library("one").also(::failing)
        val many = library("many").also(::failing)
        // Every open waits a little, so the readers really overlap.
        many.tree.queryDelayMs = 2

        val oneEvents = one.scanner(naming = TestNaming.naming(temp.newFolder("p1")), namingReaders = 1).scan().toList()
        val manyEvents = many.scanner(naming = TestNaming.naming(temp.newFolder("p4")), namingReaders = 4).scan().toList()

        assertTrue("the readers overlapped", many.tree.maxConcurrentQueries > 1)
        assertEquals(one.namesByFile(), many.namesByFile())
        assertEquals("the same progress, page by page", oneEvents.filterIsInstance<ScanEvent.Named>(), manyEvents.filterIsInstance<ScanEvent.Named>())
        assertEquals(one.tree.opened.toSet(), many.tree.opened.toSet())
        assertEquals(one.tree.opened.size, many.tree.opened.size)
        // The rows were named, not left: the comparison is not of two empty passes.
        assertTrue(many.games().count { it.title == "Zork Quest II" } >= FILES / 5 - 1)
        assertTrue(many.games().count { it.title == "Zork Quest" } == FILES / 5)
    }

    @Test
    fun progressNeverGoesBackOrPastTheTotal() = runBlocking {
        val fx = library("progress")
        val events = fx.scanner(naming = TestNaming.naming(temp.newFolder("p")), namingReaders = 4).scan().toList()

        val named = events.filterIsInstance<ScanEvent.Named>()
        assertTrue("several pages", named.size >= 3)
        val total = named.first().gamesToName
        assertTrue(named.all { it.gamesToName == total && it.gamesNamed <= total })
        assertEquals(named.map { it.gamesNamed }.sorted(), named.map { it.gamesNamed })
        assertEquals(named.map { it.gamesNamed }.distinct(), named.map { it.gamesNamed })
        assertEquals(total, named.last().gamesNamed)
    }

    @Test
    fun cancellingStopsTheReaders() = runBlocking {
        val fx = library("cancel")
        val naming = TestNaming.naming(temp.newFolder("p"))
        val readers = 4
        val allBlocked = CountDownLatch(readers)
        val release = CountDownLatch(1)
        fx.tree.beforeQuery = { id ->
            if (id.endsWith(".sfc") || id.endsWith(".gba") || id.startsWith("fs:snes/zq")) {
                allBlocked.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
        }
        val scan = launch(Dispatchers.Default) { fx.scanner(naming = naming, namingReaders = readers).scan().toList() }
        assertTrue("every reader is inside an open", allBlocked.await(10, TimeUnit.SECONDS))
        // cancel() before releasing the readers, then join (a cancelled reader must stop first, or the release hangs).
        scan.cancel()
        release.countDown()
        scan.join()

        assertEquals("no file opened after the cancel", readers, fx.tree.opened.size)
        assertTrue("nothing was named: the page was not written", fx.games().all { it.needsIdentify || it.systemId == "arcade" })
    }

    @Test
    fun oneFailingFileStopsNoOtherAndIsLogged() = runBlocking {
        val fx = library("failing")
        fx.tree.failingReads += "fs:gba/Zork Quest 2 5 (USA).gba"
        fx.scanner(naming = TestNaming.naming(temp.newFolder("p")), namingReaders = 4).scan().toList()

        val failed = fx.game("Zork Quest 2 5 (USA).gba")
        assertTrue("left for the next scan", failed.needsIdentify)
        assertEquals("every other game is named", listOf(failed.id), fx.games().filter { it.needsIdentify }.map { it.id })
        val lines = ShadowLog.getLogs().map { it.msg }
        assertTrue(lines.any { it.contains("game ${failed.id} not read this time (ClosedChannelException)") })
        assertFalse(lines.any { it.contains("Zork") || it.contains(".gba") || it.contains("ROMs") })
    }

    @Test
    fun eachPhaseLogsOneLineWithItsTimeAndFiles() = runBlocking {
        val fx = library("phases")
        fx.scanner(naming = TestNaming.naming(temp.newFolder("p")), namingReaders = 4).scan().toList()

        val lines = ShadowLog.getLogs().filter { it.tag == LOG_TAG }.map { it.msg }
        val phase = Regex("""Folder ${fx.folderId} phase (walk|database writes|naming): \d+ ms, (\d+) files, (\d+|-) files/s(; .*)?""")
        val found = lines.mapNotNull { phase.matchEntire(it) }.associate { it.groupValues[1] to it }
        assertEquals(setOf("walk", "database writes", "naming"), found.keys)
        assertEquals(FILES.toString(), found.getValue("walk").groupValues[2])
        assertEquals(FILES.toString(), found.getValue("naming").groupValues[2])
        val naming = found.getValue("naming").value
        assertTrue(naming, naming.contains("${FILES / 5 * 4} opened, open and read ") && naming.contains("ms summed over 4 at a time"))
        assertTrue(found.getValue("database writes").value.contains("$FILES rows written"))
        assertFalse(lines.any { it.contains("Zork") || it.contains("Moonlit") || it.contains("ROMs") })
        assertTrue(ShadowLog.getLogs().none { it.throwable != null })
    }

    @Test
    fun aPhaseLineGivesTimeFilesAndRate() {
        assertEquals(
            "Folder 3 phase naming: 9120 ms, 1430 files, 156 files/s; 1430 opened",
            ScanPhaseLog.line(3, ScanPhaseLog.NAMING, 9_120_400_000L, 1430, "1430 opened"),
        )
        assertEquals("Folder 1 phase walk: 0 ms, 2 files, - files/s", ScanPhaseLog.line(1, ScanPhaseLog.WALK, 400_000L, 2))
    }

    private companion object {
        /** Three pages of the pass and a bit. */
        const val FILES = 610
        const val FILE_TIME = 1_600_000_000_000L
    }
}
