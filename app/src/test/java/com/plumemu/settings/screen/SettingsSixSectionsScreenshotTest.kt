package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.secondscreen.SecondScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The six sections on a handheld with a second screen in use: the sections column as Settings opens and on its last
 * section, Library's rows to the last one, Library › Emulators' systems list and one system's page, and Box art's
 * panel in a build without ScreenScraper (the shots' content has no account), and About with Check for updates daily focused
 * right under Version.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsSixSectionsScreenshotTest {
    private val content = ShotContent.library.copy(secondScreen = SecondScreen(2, "Screen-2", ScreenKind.BuiltIn), isSecondScreenOn = true)

    private val settings
        @Composable get() = settingsForShot().copy(secondScreen = SecondScreenSettings(enabled = true))

    private fun library(option: SettingsOption) = mapOf(SettingsSection.Library to SettingsSection.Library.rowsIn(content).indexOf(option))

    @Test
    fun sectionsAsSettingsOpens() =
        ReferenceShots.capture("settings_six_sections_first") {
            SettingsAt(SettingsFocus(column = SettingsColumn.Sections, section = SettingsSection.Library), settings = settings, content = content)
        }

    @Test
    fun sectionsOnTheLast() =
        ReferenceShots.capture("settings_six_sections_last") {
            SettingsAt(SettingsFocus(column = SettingsColumn.Sections, section = SettingsSection.entries.last()), settings = settings, content = content)
        }

    @Test
    fun libraryToItsLastRow() =
        ReferenceShots.capture("settings_six_library_rows") {
            val rows = SettingsSection.Library.rowsIn(content)
            SettingsAt(SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.Library, rememberedOption = mapOf(SettingsSection.Library to rows.lastIndex)), settings = settings, content = content)
        }

    @Test
    fun screensSection() =
        ReferenceShots.capture("settings_six_screens") {
            SettingsAt(SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.HomeScreen, rememberedOption = mapOf(SettingsSection.HomeScreen to 2)), settings = settings, content = content)
        }

    @Test
    fun emulatorsSystemsList() =
        ReferenceShots.capture("settings_six_emulators_list") {
            val focus =
                SettingsFocus(
                    column = SettingsColumn.Panel,
                    section = SettingsSection.Library,
                    rememberedOption = library(SettingsOption.Emulators),
                    page = PanelPage.EmulatorSystems,
                    panelIndex = ShotContent.PSX_ROW,
                )
            SettingsAt(focus, settings = settings, content = content)
        }

    @Test
    fun emulatorsOfOneSystem() =
        ReferenceShots.capture("settings_six_emulators_system") {
            val focus =
                SettingsFocus(
                    column = SettingsColumn.Panel,
                    section = SettingsSection.Library,
                    rememberedOption = library(SettingsOption.Emulators),
                    page = PanelPage.Emulators,
                    panelIndex = 2,
                    systemId = "psx",
                )
            SettingsAt(focus, settings = settings, content = content)
        }

    @Test
    fun boxArtWithoutScreenScraper() =
        ReferenceShots.capture("settings_six_box_art_no_screenscraper") {
            SettingsAt(SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.Library, rememberedOption = library(SettingsOption.BoxArtSource)), settings = settings, content = content)
        }

    @Test
    fun aboutOnTheDailySwitch() =
        ReferenceShots.capture("settings_six_about_daily") {
            val withUpdates = content.copy(hasUpdates = true)
            val rows = SettingsSection.About.rowsIn(withUpdates)
            val focus = SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.About, rememberedOption = mapOf(SettingsSection.About to rows.indexOf(SettingsOption.CheckUpdatesDaily)))
            SettingsAt(focus, settings = settings, content = withUpdates)
        }
}
