package com.plumemu.coredata.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** `controls.switch_system_with`: L2 / R2 by default, stored, an unknown name reads as the default. */
class SettingsSwitchSystemWithTest {
    @get:Rule val tmp = TemporaryFolder()

    private val scopes = mutableListOf<Job>()
    private val file: File by lazy { File(tmp.root, "settings.preferences_pb") }

    private fun open(): SettingsRepository {
        val job = Job()
        scopes += job
        return SettingsRepository.create({ file }, CoroutineScope(Dispatchers.IO + job), SettingsLog { })
    }

    @After
    fun tearDown() = runBlocking { scopes.forEach { it.cancelAndJoin() } }

    @Test
    fun aFreshFileSwitchesSystemWithL2R2() = runBlocking {
        assertEquals(SwitchSystemWithSetting.Triggers, open().settings.first().controls.switchSystemWith)
    }

    @Test
    fun anUnknownNameReadsAsTheDefault() = runBlocking {
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit { it[SettingsCodec.Keys.switchSystemWith] = "Shoulders" }
        job.cancelAndJoin()
        assertEquals(SwitchSystemWithSetting.Triggers, open().settings.first().controls.switchSystemWith)
    }
}
