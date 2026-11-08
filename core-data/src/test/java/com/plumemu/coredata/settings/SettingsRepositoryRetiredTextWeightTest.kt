package com.plumemu.coredata.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The Text weight setting is gone: a weight stored by an older build reads without error and is dropped on the next write. */
class SettingsRepositoryRetiredTextWeightTest {
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
    fun aStoredTextWeightFromAnOlderBuildReadsWithoutErrorAndIsGoneAfterTheNextWrite() = runBlocking {
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit { it[SettingsCodec.Keys.textWeight] = "Bold"; it[SettingsCodec.Keys.textColor] = "Dark" }
        job.cancelAndJoin()
        assertEquals(TextColorSetting.Dark, open().settings.first().appearance.textColor)
        assertTrue("a retired key is not logged as damaged: $logLines", logLines.isEmpty())
        closeAll()
        open().update { it.copy(appearance = it.appearance.copy(gridSize = GridSizeSetting.Large)) }
        closeAll()

        val job2 = Job()
        val after = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job2), produceFile = { file })
        val stored = after.data.first()
        job2.cancelAndJoin()
        assertEquals("the stored text weight is dropped", null, stored[SettingsCodec.Keys.textWeight])
        assertEquals("the other values stay", "Dark", stored[SettingsCodec.Keys.textColor])
    }
}
