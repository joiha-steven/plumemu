package com.plumemu.settings.screen

import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings › Library › Unidentified games, and Hidden games with a "Not a game" row. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsUnidentifiedScreenshotTest {
    private val content =
        ShotContent.library.copy(
            unidentifiedGames =
                listOf(
                    SettingsGame(31, "mtrd fusn", "Game Boy Advance"),
                    SettingsGame(32, "My Homebrew Demo 2", "Super Nintendo"),
                    SettingsGame(33, "untitled_rom_final_v3_really_final_this_time", "Nintendo Entertainment System"),
                ),
            hiddenGames =
                listOf(
                    SettingsGame(11, "Chrono Trigger", "Super Nintendo"),
                    SettingsGame(14, "Controller Test", "Super Nintendo", isNotAGame = true),
                ),
        )

    private fun focusOn(option: SettingsOption, page: PanelPage) =
        SettingsFocus(
            column = SettingsColumn.Panel,
            section = SettingsSection.Library,
            rememberedOption = mapOf(SettingsSection.Library to SettingsSection.Library.rowsIn(content).indexOf(option)),
            page = page,
            panelIndex = 1,
        )

    @Test
    fun unidentifiedGames() =
        ReferenceShots.capture("settings_unidentified_games") { SettingsAt(focusOn(SettingsOption.UnidentifiedGames, PanelPage.UnidentifiedGames), settingsForShot(), content) }

    @Test
    fun hiddenWithNotAGame() =
        ReferenceShots.capture("settings_hidden_not_a_game") { SettingsAt(focusOn(SettingsOption.HiddenGames, PanelPage.HiddenGames), settingsForShot(), content) }
}
