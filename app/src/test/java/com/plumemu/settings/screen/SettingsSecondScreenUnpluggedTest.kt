package com.plumemu.settings.screen

import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.secondscreen.SecondScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A second-screen row's panel open when the screen is unplugged: the panel closes and the cursor stays in the rows, never on another row's panel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsSecondScreenUnpluggedTest {
    private val stored = SecondScreenSettings(enabled = true)
    private val screen = SecondScreen(4, "Screen-2", ScreenKind.BuiltIn)
    private val withScreen = SettingsContent().withSecondScreen(screen, stored)

    private fun unpluggedFrom(option: SettingsOption): SettingsModel {
        val rows = SettingsSection.HomeScreen.rowsIn(withScreen)
        val focus = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.HomeScreen, rememberedOption = mapOf(SettingsSection.HomeScreen to rows.indexOf(option)))
        val model = SettingsModel(NoActions, focus)
        model.content = withScreen
        assertEquals(option, model.panelOption)
        model.content = withScreen.withSecondScreen(null, stored)
        return model
    }

    private fun assertBackInTheRows(option: SettingsOption) {
        val model = unpluggedFrom(option)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertNull(model.focus.page)
        assertEquals("the nearest row still there", SettingsSection.HomeScreen.rowsIn(model.content).last(), model.focusedOption)
    }

    @Test
    fun duringAGamesPanel() = assertBackInTheRows(SettingsOption.SecondScreenInGame)

    @Test
    fun openAppsOnsPanel() = assertBackInTheRows(SettingsOption.SecondScreenAppsOn)

    @Test
    fun useSecondScreensPanel() = assertBackInTheRows(SettingsOption.SecondScreen)
}
