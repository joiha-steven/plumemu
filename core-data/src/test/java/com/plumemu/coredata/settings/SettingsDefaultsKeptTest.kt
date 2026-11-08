package com.plumemu.coredata.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** Moving a default (Jost, the page and accent of Plumemu) never rewrites what a person stored. */
class SettingsDefaultsKeptTest {
    private val codec = SettingsCodec(SettingsLog { })

    @Test
    fun aStoredAccentIsKept() {
        val stored = mutablePreferencesOf(SettingsCodec.Keys.accent to "Red")
        val read = codec.decode(stored).appearance
        assertEquals(ColorSetting.Preset("Red"), read.accent)
    }

    @Test
    fun nothingStoredReadsTheNewDefaults() {
        val read = codec.decode(emptyPreferences()).appearance
        assertEquals(ColorSetting.Custom(0x3A7CEB), read.accent)
        assertEquals(ColorSetting.Custom(0x161A24), read.background)
    }

    @Test
    fun aStoredOldDefaultIsNeverRewritten() {
        val stored = mutablePreferencesOf(SettingsCodec.Keys.background to "Black", SettingsCodec.Keys.accent to "#3E7BE0")
        val read = codec.decode(stored).appearance
        assertEquals(ColorSetting.Preset("Black"), read.background)
        assertEquals(ColorSetting.Custom(0x3E7BE0), read.accent)
    }
}
