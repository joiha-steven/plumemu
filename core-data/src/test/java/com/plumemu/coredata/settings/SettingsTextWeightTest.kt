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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** `appearance.text_weight`: stored, read back, absent in an older file, unknown names ignored. */
class SettingsTextWeightTest {
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
    fun textWeightRoundTripsAndAFileWithoutTheKeyReadsAsNotPicked() = runBlocking {
        assertNull("a fresh file has no text weight", open().settings.first().appearance.textWeight)
        closeAll()
        for (weight in TextWeightSetting.entries) {
            open().setAppearance(AppearanceSettings(textWeight = weight))
            closeAll()
            assertEquals(weight, open().settings.first().appearance.textWeight)
            closeAll()
        }
        assertEquals(listOf("Thin", "Medium", "Bold"), TextWeightSetting.entries.map { it.name })
        open().setAppearance(AppearanceSettings(textWeight = null))
        closeAll()
        assertNull(open().settings.first().appearance.textWeight)
    }

    @Test
    fun aTextWeightNameThisVersionDoesNotKnowReadsAsNotPickedAndLogsTheKey() = runBlocking {
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit { it[SettingsCodec.Keys.textWeight] = "Heavy" }
        job.cancelAndJoin()
        assertNull(open().settings.first().appearance.textWeight)
        assertTrue(logLines.toString(), logLines.any { "appearance.text_weight" in it })
        assertTrue("values are never logged: $logLines", logLines.none { "Heavy" in it })
    }
}
