package com.plumemu.settings

import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SettingsRepository
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.testing.ReferenceShots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** The startup settling of the button set, on a real settings file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class ButtonSetSettleTest {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repository = SettingsRepository.create(ApplicationProvider.getApplicationContext(), scope)
    private val logLines = mutableListOf<String>()

    private fun stored(): AppearanceSettings = runBlocking { repository.settings.first().appearance }

    private fun ivoryColours() =
        AppearanceSettings(background = ColorSet.Ivory.background.toSetting(), textColor = ColorSet.Ivory.textMode.toSetting(), accent = ColorSet.Ivory.accent.toSetting())

    @Test
    fun oldIvoryColoursSettleToIvoryAndAnExistingNoneIsNotOverwritten() =
        runBlocking {
            repository.setAppearance(ivoryColours())
            repository.settleButtonSet { logLines += it }
            assertEquals("Ivory", stored().buttonSet)
            // Colours changed by hand afterwards: settling again keeps the decided value.
            repository.setAppearance(stored().copy(background = com.plumemu.coredata.settings.ColorSetting.Preset("Graphite")))
            repository.settleButtonSet { logLines += it }
            assertEquals("Ivory", stored().buttonSet)
            // A stored None stays None even when the colours are Ivory's.
            repository.setAppearance(ivoryColours().copy(buttonSet = AppearanceSettings.BUTTONS_NONE))
            repository.settleButtonSet { logLines += it }
            assertEquals(AppearanceSettings.BUTTONS_NONE, stored().buttonSet)
            assertTrue(logLines.isEmpty())
            scope.cancel()
        }

    @Test
    fun aFailedWriteIsLoggedByClassAndNotThrown() =
        runBlocking {
            settleButtonSet({ throw IOException("disk full") }, { logLines += it })
            assertEquals(1, logLines.size)
            assertTrue(logLines.single(), logLines.single().contains("IOException"))
            assertTrue(PlumSettings.Defaults.appearance.buttonSet == null)
            scope.cancel()
        }
}
