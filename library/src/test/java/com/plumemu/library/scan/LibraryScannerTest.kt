package com.plumemu.library.scan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.library.TestRules
import com.plumemu.library.catalog.CatalogSettings
import com.plumemu.identify.name.Region
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryScannerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var db: PlumDatabase
    private lateinit var roms: File
    private lateinit var tree: FileSystemDocumentTree
    private val permissions = FakeFolderPermissions()
    private var folderId = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
        roms = File(temp.root, "ROMs")
        File("src/test/fixtures/roms").copyRecursively(roms)
        // Fixed, distinct modified times: a rescan must not depend on the clock's resolution.
        roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(BASE_TIME) }
        tree = FileSystemDocumentTree(roms)
        permissions.held += TREE_URI
        folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = TREE_URI, displayName = "ROMs", addedAt = 0))
    }

    @After
    fun tearDown() = db.close()

    private fun scanner(batchSize: Int = LibraryScanner.DEFAULT_BATCH_SIZE) =
        LibraryScanner(db, permissions, { tree }, TestRules.ALL, batchSize = batchSize, clock = { 42 })

    private suspend fun scan(batchSize: Int = LibraryScanner.DEFAULT_BATCH_SIZE): List<ScanEvent> {
        tree.listed.clear()
        return scanner(batchSize).scan().toList()
    }

    private suspend fun games(): List<GameEntity> = db.scanDao().gamesInFolder(folderId)

    private suspend fun shownTitles() = games().filter { it.isPrimaryCopy }.map { "${it.systemId}:${it.title}" }.sorted()

    private fun outcome(events: List<ScanEvent>) = events.filterIsInstance<ScanEvent.FolderFinished>().single().outcome

    /** Changes a directory's modified time the way adding or removing a child does. */
    private fun touch(dir: File, step: Long = 1) = dir.setLastModified(BASE_TIME + step * 10_000)

    @Test
    fun firstScanFindsTheGamesAndSkipsJunkBiosAndUnknownFiles() = runTest {
        val events = scan()
        assertEquals(
            listOf(
                "arcade:sf2",
                "gba:Pokemon - Emerald Version",
                "megadrive:Sonic the Hedgehog",
                "psx:Crash Bandicoot",
                "psx:Final Fantasy VII",
                "psx:Metal Gear Solid",
                "psx:Unknown Game",
                "snes:Mario Hack",
                "snes:Super Mario World",
                "snes:The Legend of Zelda - A Link to the Past",
            ),
            shownTitles(),
        )
        val stats = (outcome(events) as FolderOutcome.Scanned).stats
        assertEquals(11, stats.gamesAdded) // the European Super Mario World is stored as a grouped copy
        assertEquals(42L, db.scanDao().folders().single().lastScanFinishedAt)
        assertEquals("snes/hacks/Mario Hack.smc", games().single { it.title == "Mario Hack" }.relativePath)
        assertTrue("bios folder is never listed", tree.listed.none { it.contains("bios") })
    }

    @Test
    fun emptyFolderScansToAnEmptyLibrary() = runTest {
        roms.deleteRecursively()
        roms.mkdirs()
        val events = scan()
        assertEquals(emptyList<GameEntity>(), games())
        assertEquals(0, (outcome(events) as FolderOutcome.Scanned).stats.gamesAdded)
    }

    @Test
    fun rescanWithNoChangeListsNothingAndWritesNothing() = runTest {
        scan()
        val before = games()
        val events = scan()
        assertEquals(emptyList<String>(), tree.listed)
        val stats = (outcome(events) as FolderOutcome.Scanned).stats
        assertEquals(0, stats.directoriesListed)
        assertEquals(8, stats.directoriesUnchanged) // every directory except the skipped bios folder
        assertEquals(before, games())
    }

    @Test
    fun addedFileAppearsAndOnlyItsDirectoryIsListed() = runTest {
        scan()
        File(roms, "snes/Actraiser (USA).sfc").createNewFile()
        touch(File(roms, "snes"))
        scan()
        assertEquals(listOf("fs:snes"), tree.listed)
        assertTrue("snes:Actraiser" in shownTitles())
    }

    @Test
    fun removedFileDisappearsAndOtherGamesKeepTheirRows() = runTest {
        scan()
        val crash = games().single { it.title == "Crash Bandicoot" }
        File(roms, "snes/Super Mario World (USA).sfc").delete()
        touch(File(roms, "snes"))
        scan()
        assertEquals(listOf("fs:snes"), tree.listed)
        // The European copy is now the one shown.
        assertEquals("Super Mario World (Europe).sfc", games().single { it.title == "Super Mario World" }.fileName)
        assertTrue(games().single { it.title == "Super Mario World" }.isPrimaryCopy)
        assertEquals(crash, games().single { it.title == "Crash Bandicoot" })
    }

    @Test
    fun deepChangeIsFoundWithoutListingUnchangedParents() = runTest {
        scan()
        File(roms, "snes/hacks/Second Hack.smc").createNewFile()
        touch(File(roms, "snes/hacks"))
        scan()
        assertEquals(listOf("fs:snes/hacks"), tree.listed)
        assertTrue("snes:Second Hack" in shownTitles())
    }

    @Test
    fun removedSubdirectoryTakesItsGamesAndNewSubdirectoryIsWalked() = runTest {
        scan()
        File(roms, "snes/hacks").deleteRecursively()
        File(roms, "snes/translations/deep").mkdirs()
        File(roms, "snes/translations/deep/Mother 2 (T-En).sfc").createNewFile()
        touch(File(roms, "snes"))
        scan()
        assertEquals(listOf("fs:snes", "fs:snes/translations", "fs:snes/translations/deep"), tree.listed)
        assertTrue("snes:Mario Hack" !in shownTitles())
        assertTrue("snes:Mother 2" in shownTitles())
        assertTrue(db.scanDao().directories(folderId).none { it.relativePath == "snes/hacks" })
        // And the next rescan is quiet again.
        scan()
        assertEquals(emptyList<String>(), tree.listed)
    }

    @Test
    fun favouriteAndHiddenSurviveARescanOfTheirDirectory() = runTest {
        scan()
        val zelda = games().single { it.title.contains("Zelda") }
        db.gameDao().setFavorite(zelda.id, true)
        File(roms, "snes/Actraiser (USA).sfc").createNewFile()
        touch(File(roms, "snes"))
        scan()
        val after = games().single { it.title.contains("Zelda") }
        assertEquals(zelda.id, after.id)
        assertTrue(after.isFavorite)
    }

    @Test
    fun fileOfAnotherSystemInAMatchedFolderIsKeptAndItsFavouriteSurvivesAForcedRescan() = runTest {
        File(roms, "snes/Pokemon Ruby (USA).gba").createNewFile()
        touch(File(roms, "snes"))
        scan()
        val ruby = games().single { it.fileName == "Pokemon Ruby (USA).gba" }
        assertEquals("gba", ruby.systemId)
        db.gameDao().setFavorite(ruby.id, true)
        scanner().rescanEverything().toList()
        val after = games().single { it.fileName == "Pokemon Ruby (USA).gba" }
        assertEquals(ruby.id, after.id)
        assertTrue(after.isFavorite)
    }

    @Test
    fun revokedPermissionIsATypedResultAndKeepsTheGames() = runTest {
        scan()
        val before = games()
        permissions.held.clear()
        assertEquals(FolderOutcome.Unavailable(UnavailableReason.PERMISSION_REVOKED), outcome(scan()))
        permissions.held += TREE_URI
        tree.isPermissionRevoked = true
        assertEquals(FolderOutcome.Unavailable(UnavailableReason.PERMISSION_REVOKED), outcome(scan()))
        assertEquals(before, games())
    }

    @Test
    fun missingFolderIsATypedResultAndKeepsTheGames() = runTest {
        scan()
        val before = games()
        roms.deleteRecursively()
        assertEquals(FolderOutcome.Unavailable(UnavailableReason.FOLDER_MISSING), outcome(scan()))
        assertEquals(before, games())
    }

    @Test
    fun cardPulledDuringARescanDeletesNothing() = runTest {
        scan()
        val before = games()
        File(roms, "psx/Crash Bandicoot (USA).cue").delete()
        touch(File(roms, "psx"))
        // The psx listing succeeds, then the whole card disappears before anything is deleted.
        tree.beforeQuery = { id -> if (id != "fs:psx" && "fs:psx" in tree.listed) roms.deleteRecursively() }
        assertEquals(FolderOutcome.Unavailable(UnavailableReason.FOLDER_MISSING), outcome(scan()))
        // New finds may already be written (the lone .bin); nothing that was there is deleted.
        assertTrue(games().map { it.id }.containsAll(before.map { it.id }))
    }

    @Test
    fun unreadableSubdirectoryIsSkippedAndRetriedNextTime() = runTest {
        tree.failing += "fs:psx"
        scan()
        assertTrue(games().none { it.systemId == "psx" })
        tree.failing.clear()
        scan()
        assertTrue(tree.listed.contains("fs:psx"))
        assertTrue(games().any { it.systemId == "psx" })
    }

    @Test
    fun gamesLandInBatchesWithProgressBeforeTheEnd() = runTest {
        val many = File(roms, "gba/many").apply { mkdirs() }
        repeat(25) { File(many, "Game $it (USA).gba").createNewFile() }
        val events = scan(batchSize = 10)
        val progress = events.filterIsInstance<ScanEvent.Progress>()
        assertTrue("expected several batches, got ${progress.size}", progress.size >= 3)
        assertEquals(progress.map { it.gamesWritten }.sorted(), progress.map { it.gamesWritten })
        assertEquals(36, progress.last().gamesWritten)
    }

    @Test
    fun interruptedFirstScanIsCompletedByTheNextScanWithoutDuplicates() = runTest {
        // Stop inside the scan, as a killed process would: when the fourth directory is listed,
        // games of the first ones are written (batches of 1) and no directory is recorded yet.
        // Throwing from the collector instead raced the scan, which runs ahead on the IO
        // dispatcher behind flowOn's buffer and could finish first on a busy machine.
        tree.beforeQuery = { if (tree.listed.size == 4) throw InterruptedForTest() }
        try {
            scanner(batchSize = 1).scan().collect()
        } catch (expected: InterruptedForTest) {
            // The test interrupts the scan on purpose.
        }
        tree.beforeQuery = {}
        assertTrue(games().isNotEmpty())
        assertEquals(emptyList<Any>(), db.scanDao().directories(folderId))
        scan()
        assertEquals(11, games().size)
        assertEquals(games().map { it.documentId }.distinct().size, games().size)
    }

    @Test
    fun twoScansAtOnceLeaveOneRowPerFile() = runTest {
        val scanner = scanner()
        coroutineScope {
            val first = async { scanner.scan().toList() }
            val second = async { scanner.scan().toList() }
            first.await()
            second.await()
        }
        assertEquals(11, games().size)
    }

    @Test
    fun changedRegionPreferenceAppliesOnRescanEverythingAndKeepsMarks() = runTest {
        scan()
        val usa = games().single { it.fileName == "Super Mario World (USA).sfc" }
        db.gameDao().setFavorite(usa.id, true)
        val european = LibraryScanner(
            db, permissions, { tree }, TestRules.ALL,
            settings = CatalogSettings(preferredRegions = listOf(Region.EUROPE)),
        )
        european.scan().toList()
        assertTrue("a plain rescan of unchanged folders recomputes nothing", games().single { it.id == usa.id }.isPrimaryCopy)
        tree.listed.clear()
        european.rescanEverything().toList()
        assertEquals(8, tree.listed.size) // every directory except the skipped bios folder
        val after = games().single { it.id == usa.id }
        assertTrue(!after.isPrimaryCopy)
        assertTrue(after.isFavorite)
        assertTrue(games().single { it.fileName == "Super Mario World (Europe).sfc" }.isPrimaryCopy)
    }

    /** A second picked folder beside [roms], scanned with it, and a scanner that answers [gone] as proven deleted. */
    private inner class TwoFolders(gone: Set<String>) {
        val otherRoms = File(temp.root, "Other").also { File("src/test/fixtures/roms/gba").copyRecursively(File(it, "gba")) }
        val otherTree = FileSystemDocumentTree(otherRoms)
        val otherId: Long
        val scanner: LibraryScanner

        init {
            permissions.held += OTHER_URI
            otherId = kotlinx.coroutines.runBlocking { db.scanDao().insertFolder(RomFolderEntity(treeUri = OTHER_URI, displayName = "Other", addedAt = 0)) }
            scanner = LibraryScanner(
                db, permissions, { if (it.treeUri == OTHER_URI) otherTree else tree }, TestRules.ALL, clock = { 42 },
                presence = FolderPresence { it in gone },
            )
        }

        suspend fun scan(): Map<Long, FolderOutcome> =
            scanner.scan().toList().filterIsInstance<ScanEvent.FolderFinished>().associate { it.folderId to it.outcome }
    }

    private suspend fun assertForgotten(outcome: FolderOutcome?, gamesBefore: List<GameEntity>) {
        val expected = FolderOutcome.Deleted(gamesBefore.size, gamesBefore.groupingBy { it.systemId }.eachCount())
        assertEquals(expected, outcome)
        assertTrue(games().isEmpty())
        assertTrue(db.scanDao().folders().none { it.id == folderId })
        assertTrue(TREE_URI !in permissions.held)
    }

    @Test
    fun deletedFolderWhoseGrantAndroidRevokedIsForgottenWhenProvenGone() = runTest {
        val two = TwoFolders(gone = setOf(TREE_URI))
        two.scan()
        val before = games()
        val otherBefore = db.scanDao().gamesInFolder(two.otherId)
        assertTrue(before.isNotEmpty() && otherBefore.isNotEmpty())
        db.gameDao().setFavorite(before.first().id, true)
        roms.deleteRecursively()
        permissions.held -= TREE_URI
        val outcomes = two.scan()
        assertForgotten(outcomes[folderId], before)
        assertTrue(outcomes[two.otherId] is FolderOutcome.Scanned)
        assertEquals(otherBefore, db.scanDao().gamesInFolder(two.otherId))
        assertTrue(OTHER_URI in permissions.held)
        assertTrue(db.scanDao().folders().any { it.id == two.otherId })
    }

    @Test
    fun deletedFolderWithTheGrantStillHeldIsForgottenWhenProvenGone() = runTest {
        val two = TwoFolders(gone = setOf(TREE_URI))
        two.scan()
        val before = games()
        val otherBefore = db.scanDao().gamesInFolder(two.otherId)
        roms.deleteRecursively()
        val outcomes = two.scan()
        assertForgotten(outcomes[folderId], before)
        assertEquals(otherBefore, db.scanDao().gamesInFolder(two.otherId))
        assertTrue(OTHER_URI in permissions.held)
    }

    @Test
    fun accessRevokedDuringTheScanOfAProvenGoneFolderForgetsIt() = runTest {
        val two = TwoFolders(gone = setOf(TREE_URI))
        two.scan()
        val before = games()
        tree.isPermissionRevoked = true
        assertForgotten(two.scan()[folderId], before)
    }

    @Test
    fun folderNotProvenGoneKeepsItsGamesAndGrant() = runTest {
        // A card taken out: the presence check cannot prove anything, so nothing is deleted.
        val two = TwoFolders(gone = emptySet())
        two.scan()
        val before = games()
        roms.deleteRecursively()
        assertEquals(FolderOutcome.Unavailable(UnavailableReason.FOLDER_MISSING), two.scan()[folderId])
        permissions.held -= TREE_URI
        assertEquals(FolderOutcome.Unavailable(UnavailableReason.PERMISSION_REVOKED), two.scan()[folderId])
        assertEquals(before, games())
        assertTrue(db.scanDao().folders().any { it.id == folderId })
    }

    @Test
    fun readFailureNeverDeletesEvenWhenProvenGone() = runTest {
        val two = TwoFolders(gone = setOf(TREE_URI))
        two.scan()
        val before = games()
        tree.failing += tree.rootDocumentId
        assertEquals(FolderOutcome.Unavailable(UnavailableReason.READ_FAILED), two.scan()[folderId])
        assertEquals(before, games())
        assertTrue(TREE_URI in permissions.held)
        assertTrue(db.scanDao().folders().any { it.id == folderId })
    }

    private class InterruptedForTest : RuntimeException()

    private companion object {
        const val TREE_URI = "content://test/tree/roms"
        const val OTHER_URI = "content://test/tree/other"
        const val BASE_TIME = 1_700_000_000_000L
    }
}

internal class FakeFolderPermissions : FolderPermissions {
    val held = mutableSetOf<String>()
    var grantsOnTake = true

    override fun take(treeUri: String): Boolean {
        if (grantsOnTake) held += treeUri
        return grantsOnTake
    }

    override fun isHeld(treeUri: String) = treeUri in held

    override fun release(treeUri: String) {
        held -= treeUri
    }
}
