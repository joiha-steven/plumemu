package com.plumemu.coredata.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Skipped sub-folders: a skip is stored once whatever its case or slashes, lists its
 * parent again and nothing of another picked folder; un-skip takes it back in any case; the rows go
 * with their picked folder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SkippedSubfolderDaoTest {
    private val db = inMemoryDatabase()
    private val dao = db.skippedSubfolderDao()

    @After
    fun close() = db.close()

    private suspend fun folder(uri: String) = db.scanDao().insertFolder(RomFolderEntity(treeUri = uri, displayName = "ROMs", addedAt = 0))

    private suspend fun directories(folderId: Long, vararg paths: String) {
        db.scanDao().finishScan(ScanFinish(folderId = folderId, directories = paths.map { ScannedDirectoryEntity(folderId, "doc:$folderId:$it", it, 1_000) }))
    }

    private suspend fun times(folderId: Long) = db.scanDao().directories(folderId).associate { it.relativePath to it.lastModified }

    @Test
    fun aSkipIsStoredOnceWithoutStraySlashesAndListsOnlyItsParentAgain() = runTest {
        val roms = folder("content://tree/roms")
        val card = folder("content://tree/card")
        directories(roms, "", "SNES", "SNES/Hacks", "Hacks")
        directories(card, "", "SNES")
        assertTrue(dao.skip(roms, "/SNES/Hacks/", now = 5))
        assertFalse("the same path in another case is already skipped", dao.skip(roms, "snes/hacks", now = 6))
        assertEquals(listOf("SNES/Hacks"), dao.paths(roms))
        assertEquals(mapOf("" to 1_000L, "SNES" to 0L, "SNES/Hacks" to 1_000L, "Hacks" to 1_000L), times(roms))
        assertEquals("another picked folder is not listed again", mapOf("" to 1_000L, "SNES" to 1_000L), times(card))
        assertEquals(emptyList<String>(), dao.paths(card))
    }

    @Test
    fun thePickedFolderItselfCannotBeSkipped() = runTest {
        val roms = folder("content://tree/roms")
        assertFalse(dao.skip(roms, "", now = 1))
        assertFalse(dao.skip(roms, "//", now = 1))
        assertEquals(emptyList<String>(), dao.paths(roms))
    }

    @Test
    fun unskipInAnyCaseRemovesItAndListsItsParentAgain() = runTest {
        val roms = folder("content://tree/roms")
        directories(roms, "", "Hacks")
        dao.skip(roms, "Hacks", now = 5)
        directories(roms, "", "Hacks")
        assertTrue(dao.unskip(roms, "HACKS/"))
        assertEquals(emptyList<String>(), dao.paths(roms))
        assertEquals(0L, times(roms).getValue(""))
        assertFalse("nothing left to un-skip", dao.unskip(roms, "Hacks"))
    }

    @Test
    fun skipsAreListedByFolderAndGoWithTheirFolder() = runTest {
        val roms = folder("content://tree/roms")
        val card = folder("content://tree/card")
        dao.skip(roms, "_unsorted", now = 1)
        dao.skip(roms, "Hacks", now = 2)
        dao.skip(card, "Hacks", now = 3)
        assertEquals(
            listOf(SkippedSubfolderEntity(roms, "_unsorted", 1), SkippedSubfolderEntity(roms, "Hacks", 2), SkippedSubfolderEntity(card, "Hacks", 3)),
            dao.observeAll().first(),
        )
        db.scanDao().deleteFolder(roms)
        assertEquals(listOf(SkippedSubfolderEntity(card, "Hacks", 3)), dao.observeAll().first())
    }

    @Test
    fun resetLibraryKeepsTheSkips() = runTest {
        val id = folder("content://a")
        assertTrue(dao.skip(id, "Hacks", now = 1))
        db.scanDao().forgetLibrary()
        assertEquals("the skips are about folders, which Reset library keeps", listOf("Hacks"), dao.paths(id))
    }
}
