package com.plumemu.coredata.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** `home.show_system_info` is stored only once decided: never set reads as on, and nothing else decides it. */
class SystemInfoStoredTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun notStoredUntilDecidedThenKeepsItsValue() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val store = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "settings.preferences_pb") }
                val repo = SettingsRepository(store, SettingsLog { })
                repo.setAppearance(AppearanceSettings(font = FontSetting.Serif))
                val untouched = repo.settings.first().homeScreen
                assertNull("another save does not decide it", untouched.showSystemInfo)
                assertEquals("never set reads as on", true, untouched.isSystemInfoShown)

                repo.setHomeScreen(untouched.copy(showSystemInfo = false))
                assertEquals(false, repo.settings.first().homeScreen.showSystemInfo)
                repo.setHomeScreen(repo.settings.first().homeScreen.copy(showSystemInfo = true))
                repo.setAppearance(AppearanceSettings(font = FontSetting.Mono))
                assertEquals("an explicit on survives other saves", true, repo.settings.first().homeScreen.showSystemInfo)
            } finally {
                scope.cancel()
            }
        }
}
