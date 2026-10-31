package com.plumemu.coredata.db

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmulatorChoiceDaoTest {
    private lateinit var db: PlumDatabase
    private val dao get() = db.emulatorChoiceDao()

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun automaticDefaultIsStoredAndFollowsTheInstalledSet() = runTest {
        dao.setAutomaticSystemDefault("snes", "com.retroarch")
        dao.setAutomaticSystemDefault("snes", "com.snes9x")
        assertEquals("com.snes9x", dao.systemChoice("snes")?.emulatorId)
        dao.setAutomaticSystemDefault("snes", null)
        assertNull(dao.systemChoice("snes"))
    }

    @Test
    fun automaticDefaultNeverReplacesThePersonsChoice() = runTest {
        dao.setAutomaticSystemDefault("snes", "com.retroarch")
        dao.setPersonSystemChoice("snes", "com.snes9x")
        val kept = dao.setAutomaticSystemDefault("snes", "com.retroarch")
        assertEquals("com.snes9x", kept?.emulatorId)
        dao.setAutomaticSystemDefault("snes", null)
        assertEquals("com.snes9x", dao.systemChoice("snes")?.emulatorId)
    }

    @Test
    fun forgettingThePersonsSystemChoiceLeavesGameChoicesAndOtherSystems() = runTest {
        val gameId = insertGame()
        dao.setPersonSystemChoice("snes", "com.snes9x")
        dao.setPersonGameChoice(gameId, "snes", "com.bsnes")
        dao.setPersonSystemChoice("psx", "com.duckstation")
        dao.clearPersonSystemChoice("snes")
        assertNull(dao.systemChoice("snes"))
        assertEquals("com.bsnes", dao.gameChoice(gameId)?.emulatorId)
        assertEquals("com.duckstation", dao.systemChoice("psx")?.emulatorId)
        // The automatic default can be stored again afterwards.
        assertEquals("com.retroarch", dao.setAutomaticSystemDefault("snes", "com.retroarch")?.emulatorId)
    }

    @Test
    fun forgettingKeepsAnAutomaticDefault() = runTest {
        dao.setAutomaticSystemDefault("snes", "com.retroarch")
        dao.clearPersonSystemChoice("snes")
        assertEquals("com.retroarch", dao.systemChoice("snes")?.emulatorId)
    }

    @Test
    fun gameChoiceIsAPersonsChoiceKeptApartFromTheSystemRow() = runTest {
        val gameId = insertGame()
        dao.setAutomaticSystemDefault("snes", "com.retroarch")
        dao.setPersonGameChoice(gameId, "snes", "com.snes9x")
        dao.setPersonGameChoice(gameId, "snes", "com.bsnes")
        assertEquals("com.bsnes", dao.gameChoice(gameId)?.emulatorId)
        assertEquals(true, dao.gameChoice(gameId)?.isPersonChoice)
        assertEquals("com.retroarch", dao.systemChoice("snes")?.emulatorId)
        dao.clearGameChoice(gameId)
        assertNull(dao.gameChoice(gameId))
        assertEquals("com.retroarch", dao.systemChoice("snes")?.emulatorId)
    }

    private suspend fun insertGame(): Long {
        val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "t", displayName = "ROMs", addedAt = 0))
        db.scanDao().applyBatch(
            ScanBatch(folderId, listOf(SystemEntity("snes", "Super Nintendo")), listOf(game(folderId, "g"))),
        )
        return db.scanDao().gamesInFolder(folderId).single().id
    }

    @Test
    fun repeatedWritesLeaveOneSystemRow() = runTest {
        (1..20).map { i -> async { dao.setAutomaticSystemDefault("snes", "pkg$i") } }.awaitAll()
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM emulator_choice").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
    }
}
