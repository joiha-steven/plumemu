package com.plumemu.library.scrape

import com.plumemu.coredata.db.FixKeys
import com.plumemu.coredata.db.NameFixKind
import com.plumemu.identify.NameTrust
import com.plumemu.library.naming.NameDecision
import com.plumemu.library.scan.GameNaming
import com.plumemu.library.scan.ScanFixture
import com.plumemu.library.scan.TestNaming
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.boxFront
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.matched
import com.plumemu.scraper.MatchedBy
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The source over games a real scan named: hints without a read, renames and moves keep the result. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScrapeAfterScanTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var fx: ScanFixture
    private lateinit var naming: GameNaming

    @Before
    fun setUp() {
        fx = ScanFixture(temp.newFolder("fixture"), copyFixtures = false)
        naming = TestNaming.naming(temp.newFolder("packs"))
        write("gba/Zork Quest 2 (USA).gba", TestNaming.gbaRom())
        write("snes/zq.zip", TestNaming.zip("zq.sfc", TestNaming.ZIPPED_ROM))
        fx.roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(ScanFixture.BASE_TIME) }
    }

    @After
    fun tearDown() = fx.close()

    private fun write(path: String, bytes: ByteArray) =
        File(fx.roms, path).apply { parentFile?.mkdirs(); writeBytes(bytes); setLastModified(FILE_TIME) }

    private suspend fun scan(withNaming: Boolean = true) {
        fx.scanner(naming = naming.takeIf { withNaming }).scan().toList()
        fx.tree.opened.clear()
    }

    private fun source() = LibraryScrapeSource(fx.db, { fx.tree }, { null }, { "us" }, { "en" }, { ScrapeTestLibrary.NOW })

    @Test
    fun gamesStillToNameAreNotAskedYet() = runTest {
        scan(withNaming = false)
        assertEquals(emptyList<Any>(), source().pending(10))
        scan()
        assertEquals(2, source().pending(10).size)
    }

    @Test
    fun requestsCarryTheIdentifiersReadAndNothingIsOpened() = runTest {
        scan()
        val requests = source().pending(10).associateBy { it.rom.fileName }
        val zip = requests.getValue("zq.zip")
        val zipKey = FixKeys.split(fx.game("zq.zip").fixKeys).first()
        assertTrue(zipKey, zipKey.startsWith("crc:"))
        assertEquals(zipKey.removePrefix("crc:").substringBefore(':').uppercase(), zip.hints?.crc32)
        assertEquals(TestNaming.ZIPPED_ROM.size.toLong(), zip.hints?.sizeBytes)
        assertEquals(listOf("us", "eu", "wor", "ss"), zip.regions)
        val gba = requests.getValue("Zork Quest 2 (USA).gba")
        assertEquals("ZZZE", gba.hints?.serial)
        assertEquals("Zork Quest II", gba.title)
        source().deliver(zip, matched(), listOf(boxFront()))
        assertEquals("no second read", emptyList<String>(), fx.tree.opened)
    }

    @Test
    fun aRomSourceOpensTheFileThroughTheTreeWhenAsked() = runTest {
        scan()
        val zip = source().pending(10).single { it.rom.fileName == "zq.zip" }
        val bytes = zip.rom.open().use { it.readBytes() }
        assertArrayEquals(File(fx.roms, "snes/zq.zip").readBytes(), bytes)
        File(fx.roms, "snes/zq.zip").delete()
        val failed = runCatching { zip.rom.open() }.exceptionOrNull()
        assertTrue("$failed", failed is java.io.IOException)
        assertTrue("no file name in the message: ${failed?.message}", failed?.message?.contains("zq") == false)
    }

    @Test
    fun aRenamedAndMovedFileKeepsItsResult() = runTest {
        scan()
        val source = source()
        source.pending(10).forEach { source.deliver(it, matched(), listOf(boxFront())) }
        val keyBefore = FixKeys.split(fx.game("zq.zip").fixKeys).first()
        val moved = File(fx.roms, "snes/hacks/Zork (renamed).zip").also { it.parentFile?.mkdirs() }
        File(fx.roms, "snes/zq.zip").renameTo(moved)
        moved.setLastModified(FILE_TIME)
        File(fx.roms, "gba/Zork Quest 2 (USA).gba").renameTo(File(fx.roms, "gba/zq2.gba"))
        fx.touch("snes")
        fx.touch("gba")
        scan()
        val zip = fx.game("Zork (renamed).zip")
        assertEquals(keyBefore, FixKeys.split(zip.fixKeys).first())
        assertEquals("1245", fx.db.scrapedGameDao().get(keyBefore)?.screenScraperId)
        assertEquals(emptyList<String>(), source.pending(10).map { it.rom.fileName })
        assertEquals(2, fx.db.scrapedGameDao().countWithBoxFront())
    }

    @Test
    fun aStoredTitleIsShownAgainWhenTheGameIsNamedAnew() = runTest {
        write("snes/Unknown Thing (Europe).sfc", ByteArray(64))
        fx.roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(ScanFixture.BASE_TIME) }
        scan()
        val id = fx.game("Unknown Thing (Europe).sfc").id
        val source = source()
        source.deliver(source.pending(10).single { it.rom.fileName == "Unknown Thing (Europe).sfc" }, matched(title = "Unknown Thing Deluxe"), listOf(boxFront()))
        assertEquals("Unknown Thing Deluxe" to "SCRAPER_LIKELY", fx.game("Unknown Thing (Europe).sfc").let { it.title to it.nameTrust })

        fx.db.identifyDao().setFix(id, NameFixKind.OWN_NAME, "Mine", "mine", null, fixedAt = 1)
        assertEquals("Mine", fx.game("Unknown Thing (Europe).sfc").title)
        fx.db.identifyDao().backToAutomatic(id)
        fx.scanner(naming = naming).nameWaiting().toList()
        val after = fx.game("Unknown Thing (Europe).sfc")
        assertEquals("the stored title comes back, not the file name", "Unknown Thing Deluxe" to "SCRAPER_LIKELY", after.title to after.nameTrust)
    }

    @Test
    fun thePersonsPickKeepsItsNameThroughANewNamingAndComesBackAfterBackToAutomatic() = runTest {
        scan()
        val file = "Zork Quest 2 (USA).gba"
        val id = fx.game(file).id
        assertEquals(NameTrust.IDENTIFIER_EXACT.name, fx.game(file).nameTrust)
        val picked = matched(title = "Zork Quest II", by = MatchedBy.PersonChoice)
        source().storePersonChoice(id, PersonChoice.Picked(picked, listOf(boxFront())))
        assertEquals("Zork Quest II" to NameTrust.SCRAPER_PERSON_CHOICE.name, fx.game(file).let { it.title to it.nameTrust })

        // Named again (a new name pack, a rescan that reads the file again): the identifier's exact name does not win.
        fx.db.openHelper.writableDatabase.execSQL("UPDATE game SET needs_identify = 1 WHERE id = $id")
        fx.scanner(naming = naming).nameWaiting().toList()
        assertEquals("Zork Quest II" to NameTrust.SCRAPER_PERSON_CHOICE.name, fx.game(file).let { it.title to it.nameTrust })

        fx.db.identifyDao().setFix(id, NameFixKind.OWN_NAME, "Mine", "mine", null, fixedAt = 1)
        assertEquals("Mine", fx.game(file).title)
        fx.db.identifyDao().backToAutomatic(id)
        fx.scanner(naming = naming).nameWaiting().toList()
        assertEquals("Zork Quest II" to NameTrust.SCRAPER_PERSON_CHOICE.name, fx.game(file).let { it.title to it.nameTrust })
    }

    @Test
    fun aFileWhoseContentChangedInPlaceLosesTheOldContentsPick() = runTest {
        scan()
        val file = "Zork Quest 2 (USA).gba"
        val before = fx.game(file)
        source().storePersonChoice(before.id, PersonChoice.Picked(matched(title = "Picked For The Old Content", by = MatchedBy.PersonChoice), listOf(boxFront())))
        assertEquals("Picked For The Old Content", fx.game(file).title)

        // Another revision written over the same file: same row, another first key.
        write("gba/$file", ByteArray(64)).setLastModified(FILE_TIME + 60_000)
        fx.roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(ScanFixture.BASE_TIME + 3_600_000) }
        scan()
        val after = fx.game(file)
        assertEquals("the same row, named again", before.id, after.id)
        assertTrue(FixKeys.forGame(after.id, after.fixKeys).first() != FixKeys.forGame(before.id, before.fixKeys).first())
        assertEquals("the old content's pick is gone", "Zork Quest II" to NameTrust.IDENTIFIER_LIKELY.name, after.title to after.nameTrust)

        // A run then stores the new content's game, and its title is judged by the usual trust
        // order again (with the pick's trust kept, even an exact match was refused).
        val source = source()
        source.deliver(source.pending(10).single { it.rom.fileName == file }, matched(title = "Zork Quest 2 Rev B", by = MatchedBy.Hash), listOf(boxFront()))
        val newKey = FixKeys.forGame(after.id, after.fixKeys).first()
        assertEquals("Zork Quest 2 Rev B", fx.db.scrapedGameDao().get(newKey)?.title)
        assertTrue(NameDecision.takesScraperName(fx.game(file).nameTrust, NameTrust.SCRAPER_EXACT, fix = null))
    }

    @Test
    fun noBoxArtAfterAPickGivesTheIdentifiersNameBackOnTheNextNaming() = runTest {
        scan()
        val file = "Zork Quest 2 (USA).gba"
        val identified = fx.game(file)
        val source = source()
        source.storePersonChoice(identified.id, PersonChoice.Picked(matched(title = "Wrong Pick", by = MatchedBy.PersonChoice), listOf(boxFront())))
        assertEquals("Wrong Pick", fx.game(file).title)

        assertEquals(PersonChoiceStore.STORED_TO_NAME_AGAIN, source.storePersonChoice(identified.id, PersonChoice.NoBoxArt))
        fx.scanner(naming = naming).nameWaiting().toList()

        assertEquals(identified.title to identified.nameTrust, fx.game(file).let { it.title to it.nameTrust })
        assertEquals(false, fx.game(file).needsIdentify)
    }

    private companion object {
        const val FILE_TIME = 1_600_000_000_000L
    }
}
