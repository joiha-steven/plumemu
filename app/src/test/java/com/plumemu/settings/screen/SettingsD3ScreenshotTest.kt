package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings › Library with Sort games and Hidden games. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsD3ScreenshotTest {
    private val content =
        ShotContent.library.copy(
            hiddenGames =
                listOf(
                    SettingsGame(11, "Chrono Trigger", "Super Nintendo"),
                    SettingsGame(12, "Metroid Fusion", "Game Boy Advance"),
                    SettingsGame(13, "The Legend of Zelda: A Link to the Past and Four Swords", "Game Boy Advance"),
                ),
        )

    private val settings: PlumSettings
        @Composable get() = settingsForShot().let { it.copy(library = it.library.copy(sortGames = SortGamesSetting.LastPlayed)) }

    @Test
    fun librarySort() = ReferenceShots.capture("settings_library_sort") { SettingsAt(optionsAt(SettingsOption.SortGames), settings, content) }

    @Test
    fun hiddenGames() =
        ReferenceShots.capture("settings_hidden_games") {
            val row = SettingsSection.Library.rowsIn(content).indexOf(SettingsOption.HiddenGames)
            val focus =
                SettingsFocus(
                    column = SettingsColumn.Panel,
                    section = SettingsSection.Library,
                    rememberedOption = mapOf(SettingsSection.Library to row),
                    page = PanelPage.HiddenGames,
                    panelIndex = 1,
                )
            SettingsAt(focus, settings, content)
        }
}
