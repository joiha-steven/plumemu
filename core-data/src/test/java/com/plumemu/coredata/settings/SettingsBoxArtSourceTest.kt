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

/** The box art source setting: the default that follows the account, a stored choice and an unknown value. */
class SettingsBoxArtSourceTest {
    @get:Rule val tmp = TemporaryFolder()

    private val logLines = CopyOnWriteArrayList<String>()
    private val log = SettingsLog { logLines += it }
    private val scopes = mutableListOf<Job>()
    private val file: File by lazy { File(tmp.root, "settings.preferences_pb") }

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

    @Test
    fun theBoxArtSourceFollowsTheAccountUntilChosenThenSticksAndAnUnknownOneReadsAsNotChosen() = runBlocking {
        val repo = open()
        val library = repo.settings.first().library
        assertEquals("nothing is chosen on a fresh install", null, library.boxArtSource)
        assertEquals(BoxArtSourceSetting.ScreenScraper, library.effectiveBoxArtSource(accountSignedIn = true))
        assertEquals(BoxArtSourceSetting.OwnCovers, library.effectiveBoxArtSource(accountSignedIn = false))
        repo.update { it.copy(library = it.library.copy(boxArtSource = BoxArtSourceSetting.LibretroOnly)) }
        closeAll()
        val chosen = open().settings.first().library
        assertEquals(BoxArtSourceSetting.LibretroOnly, chosen.boxArtSource)
        assertEquals("a chosen source wins over the account", BoxArtSourceSetting.LibretroOnly, chosen.effectiveBoxArtSource(accountSignedIn = true))
        closeAll()
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        assertEquals("the file holds the name", "LibretroOnly", raw.data.first()[SettingsCodec.Keys.boxArtSource])
        raw.edit { it[SettingsCodec.Keys.boxArtSource] = "Mars" }
        job.cancelAndJoin()
        assertEquals(null, open().settings.first().library.boxArtSource)
        assertTrue(logLines.toString(), logLines.any { "library.box_art_source" in it })
    }
}
