package com.plumemu.library.scan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.library.TestRules
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
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
import java.io.File

/**
 * The change check (`LibraryScanner.changedFolders`) that runs when Plumemu comes back to the front
 * or storage is mounted: it asks directory times only, finds the folders a rescan would list
 * something in, and `scan(folderIds)` then reads only those. Two picked folders, as on a device with
 * internal storage and an SD card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryChangeCheckTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var db: PlumDatabase
    private val permissions = FakeFolderPermissions()
    private lateinit var internal: Card
    private lateinit var sdCard: Card

    private inner class Card(name: String) {
        val treeUri = "content://test/tree/$name"
        val roms = File(temp.root, "$name/ROMs").also { File("src/test/fixtures/roms").copyRecursively(it) }
        val tree = FileSystemDocumentTree(roms)
        var id = 0L

        init {
            roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(BASE_TIME) }
        }

        /** Changes a directory's modified time the way adding or removing a child does. */
        fun touch(relativePath: String) = File(roms, relativePath).setLastModified(BASE_TIME + 10_000)
    }

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
        internal = Card("internal")
        sdCard = Card("sdcard")
        for (card in listOf(internal, sdCard)) {
            permissions.held += card.treeUri
            card.id = db.scanDao().insertFolder(RomFolderEntity(treeUri = card.treeUri, displayName = "ROMs", addedAt = 0))
        }
    }

    @After
    fun tearDown() = db.close()

    private fun scanner() =
        LibraryScanner(db, permissions, { folder -> if (folder.treeUri == internal.treeUri) internal.tree else sdCard.tree }, TestRules.ALL, clock = { 42 })

    private fun forgetQueries() =
        listOf(internal, sdCard).forEach {
            it.tree.listed.clear()
        }

    private fun finished(events: List<ScanEvent>) = events.filterIsInstance<ScanEvent.FolderFinished>()

    @Test
    fun nothingChangedListsNothingAndFindsNoFolder() = runTest {
        scanner().scan().toList()
        forgetQueries()
        val statsBefore = internal.tree.statCount + sdCard.tree.statCount

        val changes = scanner().changedFolders()

        assertEquals(emptySet<Long>(), changes.changedFolderIds)
        assertFalse(changes.hasChanges)
        assertEquals(emptyMap<Long, UnavailableReason>(), changes.unavailable)
        assertEquals("no directory listed", emptyList<String>(), internal.tree.listed + sdCard.tree.listed)
        // One single-row query per recorded directory of both folders, nothing else.
        val recorded = db.scanDao().directories(internal.id).size + db.scanDao().directories(sdCard.id).size
        assertEquals(recorded, changes.directoriesAsked)
        assertEquals(recorded, internal.tree.statCount + sdCard.tree.statCount - statsBefore)
    }

    @Test
    fun aGameCopiedIntoOneSystemFolderMarksOnlyThatFolderAndOnlyItIsScanned() = runTest {
        scanner().scan().toList()
        val before = db.scanDao().gamesInFolder(sdCard.id).size
        File(sdCard.roms, "gba/Advance Wars (USA).gba").writeBytes(ByteArray(64))
        sdCard.touch("gba")
        forgetQueries()

        val changes = scanner().changedFolders()
        assertEquals(setOf(sdCard.id), changes.changedFolderIds)

        val internalStats = internal.tree.statCount
        val events = scanner().scan(changes.changedFolderIds).toList()

        assertEquals("only the SD card's folder is scanned", listOf(sdCard.id), finished(events).map { it.folderId })
        assertEquals("the internal folder is not asked anything", internalStats, internal.tree.statCount)
        assertEquals(emptyList<String>(), internal.tree.listed)
        assertEquals("only the changed directory is listed", listOf("fs:gba"), sdCard.tree.listed)
        val stats = (finished(events).single().outcome as FolderOutcome.Scanned).stats
        assertEquals(1, stats.gamesAdded)
        assertEquals("the notice names the system", mapOf("gba" to 1), stats.addedBySystem)
        assertEquals(emptyMap<String, Int>(), stats.removedBySystem)
        assertEquals(before + 1, db.scanDao().gamesInFolder(sdCard.id).size)
        assertTrue(db.scanDao().gamesInFolder(sdCard.id).any { it.fileName == "Advance Wars (USA).gba" })

        // Once scanned, the same check finds nothing again.
        assertFalse(scanner().changedFolders().hasChanges)
    }

    @Test
    fun aFolderNeverScannedToTheEndCountsAsChanged() = runTest {
        val changes = scanner().changedFolders()

        assertEquals(setOf(internal.id, sdCard.id), changes.changedFolderIds)
        assertEquals("a first scan is not news", setOf(internal.id, sdCard.id), changes.neverScannedFolderIds)
        assertEquals(emptyList<String>(), internal.tree.listed + sdCard.tree.listed)
    }

    @Test
    fun aScannedFolderThatChangedIsNotNeverScanned() = runTest {
        scanner().scan().toList()
        internal.touch("snes")
        val changes = scanner().changedFolders()
        assertEquals(setOf(internal.id), changes.changedFolderIds)
        assertEquals(emptySet<Long>(), changes.neverScannedFolderIds)
    }

    @Test
    fun gamesReadBeforeAnUnreadableDirectoryAreStillCountedBySystem() = runTest {
        scanner().scan().toList()
        File(internal.roms, "snes/Brand New (USA).sfc").writeBytes(ByteArray(2048))
        internal.touch("snes")
        internal.tree.failing += "fs:snes/hacks"
        forgetQueries()

        val stats = finished(scanner().scan(setOf(internal.id)).toList()).map { (it.outcome as FolderOutcome.Scanned).stats }
        assertEquals(1, stats.sumOf { it.gamesAdded })
        assertEquals(mapOf("snes" to 1), stats.fold(emptyMap<String, Int>()) { acc, st -> acc + st.addedBySystem })
    }

    @Test
    fun gamesDeletedFromOneSystemFolderAreCountedBySystemAndNothingIsAdded() = runTest {
        scanner().scan().toList()
        val snesBefore = db.scanDao().gamesInFolder(internal.id).count { it.systemId == "snes" }
        File(internal.roms, "snes").listFiles().orEmpty().filter { it.isFile }.take(1).forEach { assertTrue(it.delete()) }
        internal.touch("snes")
        forgetQueries()

        val events = scanner().scan(scanner().changedFolders().changedFolderIds).toList()
        val stats = finished(events).map { (it.outcome as FolderOutcome.Scanned).stats }
        assertEquals(0, stats.sumOf { it.gamesAdded })
        assertEquals(emptyMap<String, Int>(), stats.fold(emptyMap<String, Int>()) { acc, s -> acc + s.addedBySystem })
        assertEquals(mapOf("snes" to 1), stats.fold(emptyMap<String, Int>()) { acc, s -> acc + s.removedBySystem })
        assertEquals(snesBefore - 1, db.scanDao().gamesInFolder(internal.id).count { it.systemId == "snes" })
    }

    @Test
    fun aRecordedDirectoryThatDoesNotAnswerCountsAsChanged() = runTest {
        scanner().scan().toList()
        internal.tree.failing += "fs:snes"

        assertEquals(setOf(internal.id), scanner().changedFolders().changedFolderIds)
    }

    @Test
    fun aRemovedCardIsUnavailableNotChangedAndKeepsItsGamesFavouritesAndPlayTimeWhenItComesBack() = runTest {
        scanner().scan().toList()
        val game = db.scanDao().gamesInFolder(sdCard.id).first { it.isPrimaryCopy }
        db.gameDao().setFavorite(game.id, true)
        db.playDao().addSession(game.id, endedAt = 100, seconds = 600)
        val gamesBefore = db.scanDao().gamesInFolder(sdCard.id).map { it.id }.sorted()
        val away = File(temp.root, "sdcard/away")
        assertTrue(sdCard.roms.renameTo(away))

        val whileOut = scanner().changedFolders()
        assertEquals(mapOf(sdCard.id to UnavailableReason.FOLDER_MISSING), whileOut.unavailable)
        assertEquals("nothing to scan", emptySet<Long>(), whileOut.changedFolderIds)
        // A full rescan while it is out (Settings, or the next start) deletes nothing either.
        val outcome = finished(scanner().scan().toList()).single { it.folderId == sdCard.id }.outcome
        assertEquals(FolderOutcome.Unavailable(UnavailableReason.FOLDER_MISSING), outcome)

        assertTrue(away.renameTo(sdCard.roms))
        val back = scanner().changedFolders()

        assertEquals(emptyMap<Long, UnavailableReason>(), back.unavailable)
        assertEquals("an unchanged card needs no scan", emptySet<Long>(), back.changedFolderIds)
        assertEquals(gamesBefore, db.scanDao().gamesInFolder(sdCard.id).map { it.id }.sorted())
        assertTrue("favourite", db.scanDao().gamesInFolder(sdCard.id).single { it.id == game.id }.isFavorite)
        assertEquals(600L, db.playDao().play(game.id)?.totalSeconds)
    }

    @Test
    fun aRevokedGrantIsUnavailableAndAskedNothing() = runTest {
        scanner().scan().toList()
        permissions.held -= internal.treeUri
        val stats = internal.tree.statCount

        val changes = scanner().changedFolders()

        assertEquals(mapOf(internal.id to UnavailableReason.PERMISSION_REVOKED), changes.unavailable)
        assertEquals(emptySet<Long>(), changes.changedFolderIds)
        assertEquals(stats, internal.tree.statCount)
    }

    @Test
    fun aDirectoryStoredAsListAgainCountsAsChanged() = runTest {
        // A directory listed in the same second as a change is stored with time 0 (FolderScan.trustedTime).
        LibraryScanner(db, permissions, { if (it.treeUri == internal.treeUri) internal.tree else sdCard.tree }, TestRules.ALL, clock = { BASE_TIME })
            .scan(setOf(internal.id)).toList()

        assertEquals(setOf(internal.id, sdCard.id), scanner().changedFolders().changedFolderIds)
    }

    @Test
    fun aFileStillBeingCopiedIsReadAgainByTheNextCheckWithItsFullSizeAndFingerprint() = runTest {
        var clockNow = BASE_TIME + 3_600_000
        val naming = TestNaming.naming(temp.newFolder("packs"))
        val named = LibraryScanner(db, permissions, { if (it.treeUri == internal.treeUri) internal.tree else sdCard.tree }, TestRules.ALL, clock = { clockNow }, naming = naming)
        named.scan().toList()
        val gba = File(sdCard.roms, "gba")
        val copying = File(gba, "Big Copy (USA).gba")
        // The file manager created the file 5 s ago and is still writing it.
        val createdAt = clockNow - 5_000
        copying.writeBytes(ByteArray(1_000) { it.toByte() })
        copying.setLastModified(createdAt)
        gba.setLastModified(createdAt)

        assertEquals(setOf(sdCard.id), named.changedFolders().changedFolderIds)
        val first = named.scan(setOf(sdCard.id)).toList()
        val firstStats = (finished(first).single().outcome as FolderOutcome.Scanned).stats
        assertEquals(1, firstStats.gamesAdded)
        assertEquals("seen as still being written", 1, firstStats.filesStillWriting)
        val partial = db.scanDao().gamesInFolder(sdCard.id).single { it.fileName == copying.name }
        assertEquals(1_000L, partial.sizeBytes)
        assertEquals(1_000L, partial.fingerprintSize)
        assertEquals("its directory is stored as read again", 0L, db.scanDao().directories(sdCard.id).single { it.relativePath == "gba" }.lastModified)

        // The copy ends 20 s later: the file grows, its own time moves, its directory's time does not.
        copying.appendBytes(ByteArray(2_000) { 7 })
        copying.setLastModified(createdAt + 20_000)
        gba.setLastModified(createdAt)
        clockNow += 90_000
        sdCard.tree.opened.clear()

        assertEquals("the next check reads it again", setOf(sdCard.id), named.changedFolders().changedFolderIds)
        val second = named.scan(setOf(sdCard.id)).toList()
        val secondStats = (finished(second).single().outcome as FolderOutcome.Scanned).stats
        assertEquals(0, secondStats.gamesAdded)
        assertEquals(1, secondStats.gamesUpdated)
        assertEquals(0, secondStats.filesStillWriting)
        val whole = db.scanDao().gamesInFolder(sdCard.id).single { it.fileName == copying.name }
        assertEquals(partial.id, whole.id)
        assertEquals(3_000L, whole.sizeBytes)
        assertEquals("fingerprint read again at the full size", 3_000L, whole.fingerprintSize)
        assertEquals(createdAt + 20_000, whole.fingerprintMtime)
        assertTrue("the file was opened again", sdCard.tree.opened.any { it.endsWith(copying.name) })

        assertFalse("then it rests", named.changedFolders().hasChanges)
    }

    @Test
    fun aFreshSaveFileBesideTheRomsDoesNotKeepTheDirectoryBeingReadAgain() = runTest {
        val clockNow = BASE_TIME + 3_600_000
        val scanner = LibraryScanner(db, permissions, { if (it.treeUri == internal.treeUri) internal.tree else sdCard.tree }, TestRules.ALL, clock = { clockNow })
        scanner.scan().toList()
        // An emulator just saved: a .srm beside the ROM, written 5 s before the check.
        val snes = File(sdCard.roms, "snes")
        File(snes, "Super Metroid (USA).srm").apply { writeBytes(ByteArray(8_192)); setLastModified(clockNow - 5_000) }
        snes.setLastModified(clockNow - 5_000)

        assertEquals(setOf(sdCard.id), scanner.changedFolders().changedFolderIds)
        val stats = (finished(scanner.scan(setOf(sdCard.id)).toList()).single().outcome as FolderOutcome.Scanned).stats

        assertEquals("a save is not a game being copied", 0, stats.filesStillWriting)
        assertEquals(0, stats.gamesAdded + stats.gamesUpdated + stats.gamesRemoved)
        assertEquals("stored with its real time", clockNow - 5_000, db.scanDao().directories(sdCard.id).single { it.relativePath == "snes" }.lastModified)
        assertFalse("the next check sees no change", scanner.changedFolders().hasChanges)
    }

    private companion object {
        const val BASE_TIME = 1_700_000_000_000L
    }
}
