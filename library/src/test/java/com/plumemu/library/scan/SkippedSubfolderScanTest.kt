package com.plumemu.library.scan

import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.library.TestRules
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Skipped sub-folders: the next scan does not read a skipped sub-folder and its games
 * leave the library the way games whose files are gone leave it (their favourite and play time go
 * with the row, as for a deleted file); nothing on disk changes; un-skipping brings them
 * back. Only that picked folder's path is skipped, compared ignoring case and stray slashes. The
 * fixture's `snes/hacks/` holds `Mario Hack.smc`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SkippedSubfolderScanTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var f: ScanFixture

    @Before
    fun setUp() {
        f = ScanFixture(temp.root)
    }

    @After
    fun tearDown() = f.close()

    private suspend fun fileNames() = f.games().map { it.fileName }.toSet()

    private fun listedHacks() = f.tree.listed.any { it.contains("hacks", ignoreCase = true) }

    @Test
    fun aSkippedSubfolderIsNotReadAndItsGamesLeaveLikeGoneFilesWhileTheFilesStay() = runTest {
        f.scan()
        val hack = f.game("Mario Hack.smc")
        val mario = f.game("Super Mario World (USA).sfc")
        f.db.gameDao().setFavorite(hack.id, true)
        f.db.playDao().addSession(hack.id, endedAt = 100, seconds = 600)

        assertTrue("case and slashes do not matter", f.scanner().skipSubfolder(f.folderId, "/SNES/Hacks/", skip = true))
        val stats = f.scan()

        assertFalse("the skipped folder is not listed", listedHacks())
        assertFalse("Mario Hack.smc" in fileNames())
        assertEquals(1, stats.gamesRemoved)
        assertNull("the row went, as for a deleted file", f.db.gameDao().game(hack.id))
        assertNull("its play time went with it, as for a deleted file", f.db.playDao().play(hack.id))
        assertEquals("the rest of the folder keeps its rows", mario.id, f.game("Super Mario World (USA).sfc").id)
        assertTrue("nothing on disk is touched", File(f.roms, "snes/hacks/Mario Hack.smc").isFile)
        assertTrue(f.db.scanDao().directories(f.folderId).none { it.relativePath.startsWith("snes/hacks") })

        val again = f.scan()
        assertFalse("a later scan still does not read it", listedHacks())
        assertEquals(0, again.gamesAdded)
    }

    @Test
    fun unskippingBringsTheGamesBackOnTheNextScan() = runTest {
        f.scan()
        f.scanner().skipSubfolder(f.folderId, "snes/hacks", skip = true)
        f.scan()
        assertFalse("Mario Hack.smc" in fileNames())

        assertTrue(f.scanner().skipSubfolder(f.folderId, "SNES/HACKS", skip = false))
        val stats = f.scan()

        assertTrue("the folder is read again although nothing on disk changed", listedHacks())
        assertTrue("Mario Hack.smc" in fileNames())
        assertEquals(1, stats.gamesAdded)
        assertFalse("nothing left to un-skip", f.scanner().skipSubfolder(f.folderId, "snes/hacks", skip = false))
    }

    @Test
    fun aSkipBeforeTheFirstScanKeepsTheFolderOutOfTheFullWalk() = runTest {
        f.db.skippedSubfolderDao().skip(f.folderId, "snes/hacks", now = 1)
        f.scan()
        assertFalse(listedHacks())
        assertFalse("Mario Hack.smc" in fileNames())
        assertTrue("its siblings are read", "Super Mario World (USA).sfc" in fileNames())
    }

    @Test
    fun aFolderOfTheSameNameElsewhereIsNotSkipped() = runTest {
        File(f.roms, "gba/hacks").mkdirs()
        File(f.roms, "gba/hacks/Emerald Hack.gba").writeBytes(ByteArray(16))
        File(f.roms, "gba/hacks").setLastModified(ScanFixture.BASE_TIME)
        File(f.roms, "gba").setLastModified(ScanFixture.BASE_TIME)
        f.scan()
        f.scanner().skipSubfolder(f.folderId, "snes/hacks", skip = true)
        f.scan()
        assertFalse("Mario Hack.smc" in fileNames())
        assertTrue("gba/hacks is another path", "Emerald Hack.gba" in fileNames())
    }

    @Test
    fun aSiblingWhoseNameStartsTheSameIsStillRead() = runTest {
        File(f.roms, "snes/hacks2").mkdirs()
        File(f.roms, "snes/hacks2/Zelda Hack.smc").writeBytes(ByteArray(16))
        File(f.roms, "snes/hacks2").setLastModified(ScanFixture.BASE_TIME)
        File(f.roms, "snes").setLastModified(ScanFixture.BASE_TIME)
        f.scanner().skipSubfolder(f.folderId, "snes/Hacks", skip = true)
        f.scan()
        assertFalse("Mario Hack.smc" in fileNames())
        assertTrue("hacks2 is not under hacks", "Zelda Hack.smc" in fileNames())
    }

    @Test
    fun aSkipWaitsForARunningScanThenTheNextScanDropsTheGames() = runBlocking {
        val opened = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var held = false
        f.tree.beforeQuery = {
            if (!held) {
                held = true
                opened.complete(Unit)
                runBlocking { release.await() }
            }
        }
        val scan = async(kotlinx.coroutines.Dispatchers.IO) { f.scan() }
        opened.await()
        val skip = async(kotlinx.coroutines.Dispatchers.IO) { f.scanner().skipSubfolder(f.folderId, "snes/hacks", skip = true) }
        delay(300)
        assertFalse("the skip waits for the scan's lock", skip.isCompleted)
        assertTrue("nothing stored while the scan runs", f.db.skippedSubfolderDao().paths(f.folderId).isEmpty())
        release.complete(Unit)
        scan.await()
        assertTrue(skip.await())
        assertTrue("the scan that ran first still read it", "Mario Hack.smc" in fileNames())
        f.tree.beforeQuery = {}
        f.scan()
        assertFalse("the scan after the skip drops its games", "Mario Hack.smc" in fileNames())
    }

    @Test
    fun anotherPickedFolderWithTheSamePathIsNotSkipped() = runTest {
        val otherRoot = File(temp.root, "Card").also { File("src/test/fixtures/roms").copyRecursively(it) }
        val otherTree = FileSystemDocumentTree(otherRoot)
        val other = f.db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://test/tree/card", displayName = "Card", addedAt = 0))
        f.permissions.held += "content://test/tree/card"
        val scanner = LibraryScanner(f.db, f.permissions, { if (it.id == other) otherTree else f.tree }, TestRules.ALL, clock = { f.now })
        scanner.scan().toList()
        scanner.skipSubfolder(f.folderId, "snes/hacks", skip = true)
        scanner.scan().toList()
        assertFalse("Mario Hack.smc" in fileNames())
        assertEquals(1, f.db.scanDao().gamesInFolder(other).count { it.fileName == "Mario Hack.smc" })
    }

    @Test
    fun subfoldersListsTheFirstLevelAsTheWalkSeesItAndSaysWhenItCannot() = runTest {
        val listing = f.scanner().subfolders(f.folderId)
        assertEquals(
            "A to Z ignoring case; bios is never read, so it is not offered",
            SubfolderListing.Listed(listOf("arcade", "gba", "My Games", "only-junk", "psx", "snes")),
            listing,
        )
        f.tree.isPermissionRevoked = true
        assertEquals(SubfolderListing.Unreadable, f.scanner().subfolders(f.folderId))
        f.tree.isPermissionRevoked = false
        f.permissions.held.clear()
        assertEquals("no grant: unreadable, nothing asked", SubfolderListing.Unreadable, f.scanner().subfolders(f.folderId))
        assertEquals(SubfolderListing.NoFolder, f.scanner().subfolders(9_999))
    }

    @Test
    fun theEmptyPathSkipsNothing() = runTest {
        assertFalse(f.scanner().skipSubfolder(f.folderId, "/", skip = true))
        f.scan()
        assertNotNull(f.games().firstOrNull { it.fileName == "Mario Hack.smc" })
    }
}
