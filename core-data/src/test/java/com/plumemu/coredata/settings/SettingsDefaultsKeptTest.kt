package com.plumemu.coredata.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** Moving a default (Jost, accent #3E7BE0) never rewrites what a person stored. */
class SettingsDefaultsKeptTest {
    private val codec = SettingsCodec(SettingsLog { })

    @Test
    fun aStoredFontAndAccentAreKept() {
        val stored = mutablePreferencesOf(SettingsCodec.Keys.font to "Sans", SettingsCodec.Keys.accent to "Red")
        val read = codec.decode(stored).appearance
        assertEquals(FontSetting.Sans, read.font)
        assertEquals(ColorSetting.Preset("Red"), read.accent)
    }

    @Test
    fun nothingStoredReadsTheNewDefaults() {
        val read = codec.decode(emptyPreferences()).appearance
        assertEquals(FontSetting.Jost, read.font)
        assertEquals(ColorSetting.Custom(0x3E7BE0), read.accent)
    }
}
