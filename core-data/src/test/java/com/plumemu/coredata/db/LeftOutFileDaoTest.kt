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
 * Files not added: a scan's end replaces the rows of the directories it listed and keeps the
 * others; a full walk replaces the folder's; a gone directory loses its rows and those below it;
 * another picked folder is never touched; the rows are capped per reason with the rest counted;
 * they go with their picked folder and with Reset library.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LeftOutFileDaoTest {
    private val db = inMemoryDatabase()
    private val dao = db.leftOutFileDao()

    @After
    fun close() = db.close()

    private suspend fun folder(uri: String) = db.scanDao().insertFolder(RomFolderEntity(treeUri = uri, displayName = "ROMs", addedAt = 0))

    private fun row(folderId: Long, path: String, reason: String = "junk", system: String? = null) =
        LeftOutFileEntity(
            folderId = folderId,
            directoryPath = path.substringBeforeLast('/', missingDelimiterValue = ""),
            relativePath = path,
            reason = reason,
            systemId = system,
        )

    private suspend fun finish(folderId: Long, replacesAll: Boolean, listed: List<String>, rows: List<LeftOutFileEntity>, removed: List<String> = emptyList()) =
        db.scanDao().finishScan(ScanFinish(folderId = folderId, removedDirectoryPaths = removed, leftOut = LeftOutWrite(replacesAll, listed, rows)))

    private suspend fun paths(folderId: Long) = dao.inFolder(folderId).map { it.relativePath ?: "+${it.more} ${it.reason} in '${it.directoryPath}'" }.sorted()

    @Test
    fun aRescanReplacesOnlyTheDirectoriesItListed() = runTest {
        val roms = folder("content://tree/roms")
        finish(roms, replacesAll = true, listed = listOf("", "PS1", "SNES"), rows = listOf(row(roms, "a.txt"), row(roms, "PS1/scph1001.bin", "bios", "psx"), row(roms, "SNES/x.srm", "save", "snes")))
        // PS1 listed again (its BIOS moved away, a save arrived); SNES and the root unchanged.
        finish(roms, replacesAll = false, listed = listOf("PS1"), rows = listOf(row(roms, "PS1/game.sav", "save", "psx")))
        assertEquals(listOf("PS1/game.sav", "SNES/x.srm", "a.txt"), paths(roms))
    }

    @Test
    fun aFullWalkReplacesEveryRowOfTheFolderAndNoOtherFolders() = runTest {
        val roms = folder("content://tree/roms")
        val card = folder("content://tree/card")
        finish(roms, replacesAll = true, listed = listOf("", "Old"), rows = listOf(row(roms, "Old/a.txt"), row(roms, "b.txt")))
        finish(card, replacesAll = true, listed = listOf(""), rows = listOf(row(card, "c.txt")))
        finish(roms, replacesAll = true, listed = listOf(""), rows = listOf(row(roms, "d.txt")))
        assertEquals(listOf("d.txt"), paths(roms))
        assertEquals("another picked folder keeps its rows", listOf("c.txt"), paths(card))
    }

    @Test
    fun aGoneDirectoryLosesItsRowsAndThoseBelowButNotANeighbourWithTheSamePrefix() = runTest {
        val roms = folder("content://tree/roms")
        finish(
            roms,
            replacesAll = true,
            listed = listOf("", "PS1", "PS1/Extras", "PS12"),
            rows = listOf(row(roms, "PS1/a.txt"), row(roms, "PS1/Extras/b.txt"), row(roms, "PS12/c.txt"), row(roms, "d.txt")),
        )
        finish(roms, replacesAll = false, listed = listOf(""), rows = listOf(row(roms, "d.txt")), removed = listOf("PS1"))
        assertEquals(listOf("PS12/c.txt", "d.txt"), paths(roms))
    }

    @Test
    fun aScanThatWritesNoLeftOutChangesNothing() = runTest {
        val roms = folder("content://tree/roms")
        finish(roms, replacesAll = true, listed = listOf(""), rows = listOf(row(roms, "a.txt")))
        db.scanDao().finishScan(ScanFinish(folderId = roms))
        assertEquals(listOf("a.txt"), paths(roms))
    }

    @Test
    fun rowsPastTheCapAreCountedOnTheirDirectorysMoreRow() = runTest {
        val roms = folder("content://tree/roms")
        val cap = LeftOutFileDao.MAX_ROWS_PER_REASON
        val pictures = (0 until cap + 30).map { row(roms, "Art/%04d.png".format(it)) }
        val saves = (0 until 3).map { row(roms, "SNES/$it.srm", "save", "snes") }
        // The scan already counted 7 more pictures of another directory past its own cap.
        val counted = LeftOutFileEntity(folderId = roms, directoryPath = "Media2", relativePath = null, reason = "junk", systemId = null, more = 7)
        finish(roms, replacesAll = true, listed = listOf("", "Art", "SNES", "Media2"), rows = pictures + saves + counted)
        val rows = dao.inFolder(roms)
        assertEquals(cap, rows.count { it.reason == "junk" && it.relativePath != null })
        assertEquals("the first by path are kept", "Art/0000.png", rows.first { it.reason == "junk" }.relativePath)
        assertEquals(mapOf("Art" to 30, "Media2" to 7), rows.filter { it.relativePath == null }.associate { it.directoryPath to it.more })
        assertEquals("another reason keeps its own allowance", 3, rows.count { it.reason == "save" })
        // A second pass over the same rows adds nothing twice.
        finish(roms, replacesAll = false, listed = listOf("Art"), rows = pictures.take(cap + 5))
        val again = dao.inFolder(roms)
        assertEquals(mapOf("Art" to 5, "Media2" to 7), again.filter { it.relativePath == null }.associate { it.directoryPath to it.more })
        assertEquals(cap + 5 + 7, again.filter { it.reason == "junk" }.sumOf { if (it.relativePath == null) it.more else 1 })
    }

    @Test
    fun rowsGoWithTheirFolderAndWithResetLibrary() = runTest {
        val roms = folder("content://tree/roms")
        val card = folder("content://tree/card")
        finish(roms, replacesAll = true, listed = listOf(""), rows = listOf(row(roms, "a.txt")))
        finish(card, replacesAll = true, listed = listOf(""), rows = listOf(row(card, "b.txt")))
        db.scanDao().deleteFolder(roms)
        assertEquals(listOf("b.txt"), dao.observeAll().first().map { it.relativePath })
        db.scanDao().forgetLibrary()
        assertEquals(emptyList<LeftOutFileEntity>(), dao.observeAll().first())
    }
}
