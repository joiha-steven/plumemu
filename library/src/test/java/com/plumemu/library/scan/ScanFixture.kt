package com.plumemu.library.scan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.library.TestRules
import com.plumemu.library.catalog.CatalogSettings
import com.plumemu.library.matching.SystemRule
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * The fixture ROM folders copied to [parent], an in-memory database with the folder recorded,
 * and a fake tree over the copy. Directory times are fixed so rescans do not depend on the clock.
 */
internal class ScanFixture(parent: File, fileIdsByName: Boolean = false, copyFixtures: Boolean = true) {
    val db: PlumDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
    val roms = File(parent, "ROMs").also { if (copyFixtures) File("src/test/fixtures/roms").copyRecursively(it) else it.mkdirs() }
    val permissions = FakeFolderPermissions().apply { held += TREE_URI }
    var now = 42L
    val folderId: Long

    init {
        roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(BASE_TIME) }
        folderId = runBlocking {
            db.scanDao().insertFolder(RomFolderEntity(treeUri = TREE_URI, displayName = "ROMs", addedAt = 0))
        }
    }

    val tree = FileSystemDocumentTree(roms, fileIdsByName)

    fun scanner(
        batchSize: Int = LibraryScanner.DEFAULT_BATCH_SIZE,
        settings: CatalogSettings = CatalogSettings(),
        naming: GameNaming? = null,
        rules: List<SystemRule> = TestRules.ALL,
        namingReaders: Int = NAMING_READERS,
    ) = LibraryScanner(db, permissions, { tree }, rules, settings, batchSize, clock = { now }, naming = naming, namingReaders = namingReaders)

    suspend fun scan(scanner: LibraryScanner = scanner()): ScanStats {
        tree.listed.clear()
        return stats(scanner.scan().toList())
    }

    suspend fun games(): List<GameEntity> = db.scanDao().gamesInFolder(folderId)

    suspend fun game(fileName: String): GameEntity = games().single { it.fileName == fileName }

    /** Changes a directory's modified time the way adding or removing a child does. */
    fun touch(relativePath: String, step: Long = 1) = File(roms, relativePath).setLastModified(BASE_TIME + step * 10_000)

    fun close() = db.close()

    companion object {
        const val TREE_URI = "content://test/tree/roms"
        const val BASE_TIME = 1_700_000_000_000L

        fun stats(events: List<ScanEvent>): ScanStats {
            val outcome = events.filterIsInstance<ScanEvent.FolderFinished>().single().outcome
            check(outcome is FolderOutcome.Scanned) { "scan failed: $outcome" }
            return outcome.stats
        }
    }
}
