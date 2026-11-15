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

/** `appearance.page_grain`: stored by name, Off when absent, an unknown name reads Off and its key is logged. */
class SettingsPageGrainTest {
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
    fun thePageGrainIsOffUntilPickedStoredByNameAndAnUnknownOneReadsAsOff() = runBlocking {
        assertEquals(PageGrainSetting.Off, open().settings.first().appearance.pageGrain)
        assertTrue("a missing key is not an unknown value: $logLines", logLines.isEmpty())
        assertEquals(listOf("Off", "Light", "Strong"), PageGrainSetting.entries.map { it.name })
        closeAll()
        open().update { it.copy(appearance = it.appearance.copy(pageGrain = PageGrainSetting.Strong)) }
        closeAll()
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        assertEquals("the file holds the name", "Strong", raw.data.first()[SettingsCodec.Keys.pageGrain])
        raw.edit { it[SettingsCodec.Keys.pageGrain] = "Heavy" }
        job.cancelAndJoin()
        assertEquals(PageGrainSetting.Off, open().settings.first().appearance.pageGrain)
        assertTrue(logLines.toString(), logLines.any { "appearance.page_grain" in it })
        assertTrue("values are never logged: $logLines", logLines.none { "Heavy" in it })
    }
}
