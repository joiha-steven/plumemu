package com.plumemu.coredata.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recent's size in the settings file, and the removed "Return here after playing". */
class RecentSizeSettingTest {
    private val warnings = mutableListOf<String>()
    private val codec = SettingsCodec { warnings += it }

    @Test
    fun recentKeepsTwentyWhenNothingIsStoredAndTheSizesAreTenTwentyFifty() {
        assertEquals(RecentSizeSetting.Twenty, codec.decode(emptyPreferences()).library.recentSize)
        assertEquals(listOf(10, 20, 50), RecentSizeSetting.entries.map { it.games })
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun eachSizeIsStoredByNameAndReadBack() {
        for (size in RecentSizeSetting.entries) {
            val p = mutablePreferencesOf()
            codec.encode(PlumSettings.Defaults.copy(library = LibrarySettings(recentSize = size)), p)
            assertEquals(size.name, p[stringPreferencesKey("library.recent_size")])
            assertEquals(size, codec.decode(p).library.recentSize)
        }
    }

    @Test
    fun anUnknownStoredSizeReadsAsTwentyAndLogsTheKeyOnly() {
        val p = mutablePreferencesOf(stringPreferencesKey("library.recent_size") to "Hundred")
        assertEquals(RecentSizeSetting.Twenty, codec.decode(p).library.recentSize)
        assertEquals(1, warnings.size)
        assertFalse(warnings.single().contains("Hundred"))
    }

    @Test
    fun aStaleReturnHereAfterPlayingIsNeitherReadNorWritten() {
        val stale = booleanPreferencesKey("home.return_here_after_playing")
        val p = mutablePreferencesOf(stale to false)
        assertEquals(PlumSettings.Defaults.homeScreen, codec.decode(p).homeScreen)
        val written = mutablePreferencesOf()
        codec.encode(PlumSettings.Defaults, written)
        assertFalse(written.contains(stale))
    }
}
