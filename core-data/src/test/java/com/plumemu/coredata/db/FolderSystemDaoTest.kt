package com.plumemu.coredata.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unknown folders as Settings › Library lists them (grouped under the folder right below the picked
 * one, answered ones left out) and the person's answer, which lists that folder again and nothing
 * of another folder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderSystemDaoTest {
    private val db = inMemoryDatabase()
    private val dao = db.folderSystemDao()

    @After
    fun close() = db.close()

    private suspend fun folder(uri: String, name: String) = db.scanDao().insertFolder(RomFolderEntity(treeUri = uri, displayName = name, addedAt = 0))

    private suspend fun directories(folderId: Long, vararg pathAndFiles: Pair<String, Int>) {
        db.scanDao().finishScan(
            ScanFinish(
                folderId = folderId,
                directories = pathAndFiles.map { (path, files) -> ScannedDirectoryEntity(folderId, "doc:$path", path, 1_000, files) },
            ),
        )
    }

    @Test
    fun unknownFoldersAreGroupedUnderTheFolderBelowThePickedOne() = runTest {
        val roms = folder("content://tree/roms", "ROMs")
        directories(roms, "" to 3, "SNES" to 0, "Xbox 360" to 1, "Xbox 360/Halo" to 2, "Wii U" to 5, "Empty" to 0)
        assertEquals(
            "the picked folder's own files and folders without candidate files are not listed",
            listOf(UnknownFolderRow(roms, "Wii U", 5), UnknownFolderRow(roms, "Xbox 360", 3)),
            dao.observeUnknownFolders().first(),
        )
        assertEquals(dao.observeUnknownFolders().first(), dao.unknownFolders(roms))
    }

    @Test
    fun anAnswerHidesTheFolderAndListsOnlyItsOwnSubtreeAgain() = runTest {
        val roms = folder("content://tree/roms", "ROMs")
        val card = folder("content://tree/card", "Card")
        directories(roms, "Xbox" to 2, "Xbox/Halo" to 1, "Xbox2" to 4)
        directories(card, "Xbox" to 7)
        dao.choose(roms, "Xbox", null, now = 5)
        assertEquals(
            "Ignore hides only this picked folder's Xbox",
            listOf(UnknownFolderRow(roms, "Xbox2", 4), UnknownFolderRow(card, "Xbox", 7)),
            dao.observeUnknownFolders().first(),
        )
        val times = db.scanDao().directories(roms).associate { it.relativePath to it.lastModified }
        assertEquals(mapOf("Xbox" to 0L, "Xbox/Halo" to 0L, "Xbox2" to 1_000L), times)
        assertEquals("another picked folder is not listed again", listOf(1_000L), db.scanDao().directories(card).map { it.lastModified })
        dao.choose(roms, "Xbox", "xbox", now = 6)
        assertEquals("a second answer replaces the first", listOf(FolderSystemEntity(roms, "Xbox", "xbox", 6)), dao.choices(roms))
        assertEquals(emptyList<FolderSystemEntity>(), dao.choices(card))
    }

    @Test
    fun removingThePickedFolderTakesItsAnswersWithIt() = runTest {
        val roms = folder("content://tree/roms", "ROMs")
        dao.choose(roms, "Xbox", "xbox", now = 5)
        db.scanDao().deleteFolder(roms)
        assertEquals(emptyList<FolderSystemEntity>(), dao.choices(roms))
    }
}
