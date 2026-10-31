package com.plumemu.library.scan

import com.plumemu.coredata.db.UnknownFolderRow
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Folders whose name matches no system (Settings › Library › Unknown folder): the scan counts the
 * files some system could open in them (junk and files no system opens left out), reports a folder
 * as new once, and scans a folder by the system the person chose; Ignore keeps today's scan and
 * takes the folder off the list. The fixture's `My Games/` holds `Mystery.iso` (PlayStation or
 * PlayStation 2: no game without a choice), `Sonic ... .md` (Mega Drive by its extension alone) and
 * `cover.png`; `only-junk/` and `bios/` hold nothing that counts; `snes/`, `gba/`, `psx/` and
 * `arcade/` name systems.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnknownFolderScanTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var fixture: ScanFixture

    @Before
    fun setUp() {
        fixture = ScanFixture(temp.root)
    }

    @After
    fun tearDown() = fixture.close()

    private suspend fun unknownFolders() = fixture.db.folderSystemDao().unknownFolders(fixture.folderId)

    @Test
    fun aScanListsOnlyTheFolderThatNamesNoSystemWithItsCandidateFiles() = runTest {
        val stats = fixture.scan()
        assertEquals(listOf(UnknownFolder("My Games", 2)), stats.newUnknownFolders)
        assertEquals(listOf(UnknownFolderRow(fixture.folderId, "My Games", 2)), unknownFolders())
    }

    @Test
    fun aNewUnknownFolderIsReportedOnceAndJunkIsNotCounted() = runTest {
        fixture.scan()
        File(fixture.roms, "Wii U/Zelda").mkdirs()
        File(fixture.roms, "Wii U/Zelda/zelda.iso").writeBytes(ByteArray(16))
        File(fixture.roms, "Wii U/._zelda.iso").writeBytes(ByteArray(4))
        File(fixture.roms, "Wii U/cover.png").writeBytes(ByteArray(4))
        File(fixture.roms, "Wii U/game.wua").writeBytes(ByteArray(4))
        fixture.touch("")

        val stats = fixture.scan()

        assertEquals("one candidate file in a folder below, junk and pictures left out", listOf(UnknownFolder("Wii U", 1)), stats.newUnknownFolders)
        assertEquals(listOf("My Games" to 2, "Wii U" to 1), unknownFolders().map { it.relativePath to it.files })
        fixture.touch("Wii U", step = 2)
        assertEquals("a folder already listed is not new again", emptyList<UnknownFolder>(), fixture.scan().newUnknownFolders)
    }

    @Test
    fun aChosenSystemScansTheFolderAsThatSystemAndKeepsOtherFilesAndMarks() = runTest {
        fixture.scan()
        val sonic = fixture.game("Sonic the Hedgehog (USA, Europe).md")
        fixture.db.openHelper.writableDatabase.execSQL("UPDATE game SET is_favorite = 1 WHERE id = ${sonic.id}")
        assertTrue("no game for the .iso before the choice", fixture.games().none { it.fileName == "Mystery.iso" })

        fixture.scanner().chooseFolderSystem(fixture.folderId, "My Games", "ps2")
        val stats = fixture.scan()

        assertEquals("ps2", fixture.game("Mystery.iso").systemId)
        val kept = fixture.game("Sonic the Hedgehog (USA, Europe).md")
        assertEquals("a file PlayStation 2 does not open still goes by its extension", "megadrive", kept.systemId)
        assertEquals(sonic.id, kept.id)
        assertTrue(kept.isFavorite)
        assertEquals("the answered folder is no longer listed", emptyList<UnknownFolderRow>(), unknownFolders())
        assertEquals(emptyList<UnknownFolder>(), stats.newUnknownFolders)
    }

    @Test
    fun ignoreKeepsTheScanAsItWasAndTakesTheFolderOffTheList() = runTest {
        fixture.scan()
        val before = fixture.games().map { it.id to it.systemId }.toSet()

        fixture.scanner().chooseFolderSystem(fixture.folderId, "My Games", null)
        assertEquals(emptyList<UnknownFolderRow>(), unknownFolders())
        val stats = fixture.scan()

        assertEquals(before, fixture.games().map { it.id to it.systemId }.toSet())
        assertEquals(emptyList<UnknownFolderRow>(), unknownFolders())
        assertEquals(emptyList<UnknownFolder>(), stats.newUnknownFolders)
    }
}
