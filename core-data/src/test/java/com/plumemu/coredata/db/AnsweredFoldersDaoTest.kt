package com.plumemu.coredata.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The answered folders Settings › Library lists under the unknown ones: every answer, Ignore included, in a stable order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnsweredFoldersDaoTest {
    private val db = inMemoryDatabase()
    private val dao = db.folderSystemDao()

    @After
    fun close() = db.close()

    private suspend fun folder(uri: String, name: String) = db.scanDao().insertFolder(RomFolderEntity(treeUri = uri, displayName = name, addedAt = 0))

    private suspend fun present(folderId: Long, vararg paths: String) =
        db.scanDao().finishScan(
            ScanFinish(folderId = folderId, directories = paths.map { ScannedDirectoryEntity(folderId, "doc:$it", it, 1_000, 0) }),
        )

    @Test
    fun answersAreListedWithTheirSystemOrNullForIgnoreAndChangeWithTheAnswer() = runTest {
        val roms = folder("content://tree/roms", "ROMs")
        val card = folder("content://tree/card", "Card")
        assertEquals(emptyList<AnsweredFolderRow>(), dao.observeAnsweredFolders().first())
        present(roms, "Xbox", "Halo")
        present(card, "Wii U")
        dao.choose(card, "Wii U", null, now = 1)
        dao.choose(roms, "Xbox", "xbox", now = 2)
        dao.choose(roms, "Halo", "xbox", now = 3)
        assertEquals(
            listOf(AnsweredFolderRow(roms, "Halo", "xbox", 0), AnsweredFolderRow(roms, "Xbox", "xbox", 0), AnsweredFolderRow(card, "Wii U", null, 0)),
            dao.observeAnsweredFolders().first(),
        )
        dao.choose(roms, "Xbox", "x360", now = 4)
        assertEquals("x360", dao.observeAnsweredFolders().first().single { it.relativePath == "Xbox" }.systemId)
    }

    @Test
    fun anAnswerThatLeftFilesUnmatchedCountsThemSoSettingsCanListTheFolderAgain() = runTest {
        val roms = folder("content://tree/roms", "ROMs")
        db.scanDao().finishScan(
            ScanFinish(
                folderId = roms,
                directories =
                    listOf(
                        ScannedDirectoryEntity(roms, "doc:Xbox", "Xbox", 1_000, 2),
                        ScannedDirectoryEntity(roms, "doc:Xbox/Halo", "Xbox/Halo", 1_000, 1),
                        ScannedDirectoryEntity(roms, "doc:Xbox2", "Xbox2", 1_000, 9),
                    ),
            ),
        )
        dao.choose(roms, "Xbox", "gone-system", now = 1)
        assertEquals(listOf(AnsweredFolderRow(roms, "Xbox", "gone-system", 3)), dao.observeAnsweredFolders().first())
    }

    @Test
    fun theFolderRemovedTakesItsAnswersAndThePinnedShortcutsFolderIsLeftOut() = runTest {
        val roms = folder("content://tree/roms", "ROMs")
        val pinned = folder(PinnedShortcutDao.FOLDER_TREE_URI, "Shortcuts")
        present(roms, "Xbox")
        present(pinned, "Steam")
        dao.choose(roms, "Xbox", "xbox", now = 1)
        dao.choose(pinned, "Steam", null, now = 1)
        assertEquals(listOf(AnsweredFolderRow(roms, "Xbox", "xbox", 0)), dao.observeAnsweredFolders().first())
        db.scanDao().deleteFolder(roms)
        assertEquals(emptyList<AnsweredFolderRow>(), dao.observeAnsweredFolders().first())
    }

    @Test
    fun anAnsweredFolderIsListedOnlyWhileTheScanHasADirectoryRecordForItOrBelowIt() = runTest {
        val roms = folder("content://tree/roms", "ROMs")
        val records = listOf(ScannedDirectoryEntity(roms, "doc:Test", "Test", 1_000, 2))
        db.scanDao().finishScan(ScanFinish(folderId = roms, directories = records))
        dao.choose(roms, "Test", "ps2", now = 1)
        assertEquals(listOf(AnsweredFolderRow(roms, "Test", "ps2", 2)), dao.observeAnsweredFolders().first())
        // The folder is deleted from the card: the next scan finishes without its record.
        db.scanDao().finishScan(ScanFinish(folderId = roms, removedDirectoryPaths = listOf("Test")))
        assertEquals(emptyList<AnsweredFolderRow>(), dao.observeAnsweredFolders().first())
        // The folder comes back: the stored answer applies and is listed again.
        db.scanDao().finishScan(ScanFinish(folderId = roms, directories = records))
        assertEquals(listOf(AnsweredFolderRow(roms, "Test", "ps2", 2)), dao.observeAnsweredFolders().first())
    }
}
