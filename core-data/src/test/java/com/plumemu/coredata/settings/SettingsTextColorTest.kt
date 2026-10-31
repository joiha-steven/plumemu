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

/** The text colour in the settings file; the other settings are in [SettingsRepositoryTest]. */
class SettingsTextColorTest {
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

    /** Text is Light, Dark or `#RRGGBB` under the same key; a file that held Light or Dark reads unchanged. */
    @Test
    fun aCustomTextColourSurvivesARestartAndOldValuesReadUnchanged() = runBlocking {
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit { it[SettingsCodec.Keys.textColor] = "Dark" }
        job.cancelAndJoin()
        assertEquals(TextColorSetting.Dark, open().settings.first().appearance.textColor)
        assertTrue("an old value is not an unknown one: $logLines", logLines.isEmpty())
        closeAll()

        val own = OwnColors(ColorSetting.Custom(0x123456), TextColorSetting.Custom(0xC8E6C9), ColorSetting.Preset("Amber"))
        open().update { it.copy(appearance = it.appearance.copy(textColor = TextColorSetting.Custom(0x203020), ownColors = own)) }
        closeAll()
        val restarted = open().settings.first().appearance
        assertEquals(TextColorSetting.Custom(0x203020), restarted.textColor)
        assertEquals(own, restarted.ownColors)
        closeAll()
        val rawJob = Job()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + rawJob), produceFile = { file })
        assertEquals("#203020", store.data.first()[SettingsCodec.Keys.textColor])
        assertEquals("#123456 #C8E6C9 Amber", store.data.first()[SettingsCodec.Keys.ownColors])
        // A value this version does not know: the default, the key logged (never the value).
        store.edit { it[SettingsCodec.Keys.textColor] = "Sepia" }
        rawJob.cancelAndJoin()
        assertEquals(TextColorSetting.Light, open().settings.first().appearance.textColor)
        assertTrue(logLines.any { it.contains("appearance.text_color") && !it.contains("Sepia") })
        closeAll()
    }

    /** Accent color's By system is stored as `BySystem:<fallback>` under the accent key and in the own colours; old values read unchanged. */
    @Test
    fun bySystemSurvivesARestartKeepsItsFallbackAndOldAccentsReadUnchanged() = runBlocking {
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit {
            it[SettingsCodec.Keys.accent] = "Blue"
            it[SettingsCodec.Keys.background] = "#123456"
        }
        job.cancelAndJoin()
        val old = open().settings.first().appearance
        assertEquals(ColorSetting.Preset("Blue"), old.accent)
        assertEquals(ColorSetting.Custom(0x123456), old.background)
        assertTrue("old values are not unknown ones: $logLines", logLines.isEmpty())
        closeAll()

        val own = OwnColors(ColorSetting.Custom(0x123456), TextColorSetting.Light, ColorSetting.BySystem(ColorSetting.Custom(0x336699)))
        open().update { it.copy(appearance = it.appearance.copy(accent = ColorSetting.BySystem(ColorSetting.Preset("Amber")), ownColors = own)) }
        closeAll()
        val restarted = open().settings.first().appearance
        assertEquals(ColorSetting.BySystem(ColorSetting.Preset("Amber")), restarted.accent)
        assertEquals(own, restarted.ownColors)
        closeAll()
        val rawJob = Job()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + rawJob), produceFile = { file })
        assertEquals("BySystem:Amber", store.data.first()[SettingsCodec.Keys.accent])
        assertEquals("#123456 Light BySystem:#336699", store.data.first()[SettingsCodec.Keys.ownColors])
        // Only the accent has it: a background that says so is unknown (default, key logged); so is a fallback that is itself By system.
        store.edit {
            it[SettingsCodec.Keys.background] = "BySystem:Red"
            it[SettingsCodec.Keys.accent] = "BySystem:BySystem:Red"
        }
        rawJob.cancelAndJoin()
        val damaged = open().settings.first().appearance
        assertEquals(PlumSettings.Defaults.appearance.background, damaged.background)
        assertEquals(PlumSettings.Defaults.appearance.accent, damaged.accent)
        assertTrue(logLines.any { it.contains("appearance.background") } && logLines.any { it.contains("appearance.accent") })
        closeAll()
    }
}
