package com.plumemu.coredata.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The grid-or-list choice per system and per home entry, on a real Preferences file (split from [SettingsRepositoryTest]). */
class SettingsRepositoryGridTest {
    @get:Rule val tmp = TemporaryFolder()

    private val logLines = CopyOnWriteArrayList<String>()
    private val log = SettingsLog { logLines += it }
    private val scopes = mutableListOf<Job>()

    private val file: File by lazy { File(tmp.root, "settings.preferences_pb") }

    /** A repository on [file]; only one may be open on a file at a time, so [closeAll] first. */
    private fun open(): SettingsRepository {
        val job = Job()
        scopes += job
        return SettingsRepository.create({ file }, CoroutineScope(Dispatchers.IO + job), log)
    }

    private suspend fun closeAll() {
        scopes.forEach { it.cancelAndJoin() }
        scopes.clear()
    }

    @After
    fun tearDown() = runBlocking { closeAll() }


    /** SELECT's choice outlives the process (an update once lost it on a Thor). */
    @Test
    fun gridSystemsSurviveARestartAndAFileFromBeforeThemReadsEveryList() = runBlocking {
        // An older install: other keys written, no grid systems.
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit { it[SettingsCodec.Keys.gridSize] = "Large" }
        job.cancelAndJoin()
        val old = open().settings.first().appearance
        assertEquals("the person's other choices stay", GridSizeSetting.Large, old.gridSize)
        assertEquals("every system reads as a list", emptySet<String>(), old.gridSystems)
        assertTrue("a missing key is not an unknown value: $logLines", logLines.isEmpty())
        closeAll()

        // An id no system has (its folder was removed) is kept like any other, never refused.
        open().update { it.copy(appearance = it.appearance.copy(gridSystems = setOf("snes", "gone-system"))) }
        closeAll()
        val restarted = open()
        assertEquals("a new repository on the same file reads it back", setOf("snes", "gone-system"), restarted.settings.first().appearance.gridSystems)
        // Another setting written later leaves it alone.
        restarted.update { it.copy(appearance = it.appearance.copy(gridSize = GridSizeSetting.Medium)) }
        closeAll()
        val rawJob = Job()
        val readAgain = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + rawJob), produceFile = { file }).data.first()
        rawJob.cancelAndJoin()
        assertEquals("stored as a string set under appearance.grid_systems", setOf("snes", "gone-system"), readAgain[SettingsCodec.Keys.gridSystems])
    }

    /**
     * Recent, Favorites and Apps switch too, stored under their entry
     * ids beside the systems'. A file from before holds only systems and reads unchanged.
     */
    @Test
    fun recentFavoritesAndAppsAreRememberedBesideTheSystemsAndAnOldFileReadsUnchanged() = runBlocking {
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit { it[SettingsCodec.Keys.gridSystems] = setOf("snes", "psp") }
        job.cancelAndJoin()
        val old = open().settings.first().appearance.gridSystems
        assertEquals("a file from before the entries' grids reads as it was", setOf("snes", "psp"), old)
        assertTrue("nothing unknown in it: $logLines", logLines.isEmpty())
        closeAll()

        open().update { it.copy(appearance = it.appearance.copy(gridSystems = it.appearance.gridSystems + setOf("recent", "favorites", "apps") - "psp")) }
        closeAll()
        assertEquals(
            "a new repository on the same file reads the entries back with the systems",
            setOf("snes", "recent", "favorites", "apps"),
            open().settings.first().appearance.gridSystems,
        )
        closeAll()
    }
}
