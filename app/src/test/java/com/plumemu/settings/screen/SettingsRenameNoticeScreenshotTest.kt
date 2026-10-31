package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.plumemu.coreui.components.LocalScanPulsePhase
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Library › Unidentified games right after a rename whose box art could not be looked up
 * (ScreenScraper's daily limit): the notice "Name saved, box art not looked up. …" under the
 * panel's text, on the row-text line like the rest of it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsRenameNoticeScreenshotTest {
    private val content =
        ShotContent.library.copy(
            unidentifiedGames =
                listOf(
                    SettingsGame(31, "mtrd fusn", "Game Boy Advance"),
                    SettingsGame(33, "untitled_rom_final_v3_really_final_this_time", "Nintendo Entertainment System"),
                ),
        )

    private val focus =
        SettingsFocus(
            column = SettingsColumn.Panel,
            section = SettingsSection.Library,
            rememberedOption = mapOf(SettingsSection.Library to SettingsSection.Library.rowsIn(content).indexOf(SettingsOption.UnidentifiedGames)),
            page = PanelPage.UnidentifiedGames,
            panelIndex = 0,
        )

    @Composable
    private fun WithNotice() {
        val model =
            remember {
                SettingsModel(NoActions, focus).also {
                    it.content = content
                    it.work.say(SettingsOption.UnidentifiedGames, SettingsNotice.RenameDailyLimit)
                }
            }
        CompositionLocalProvider(LocalScanPulsePhase provides 0.5f) {
            SettingsLayout(settingsForShot(), model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
        }
    }

    @Test
    fun renameNotice() = ReferenceShots.capture("settings_rename_notice") { WithNotice() }
}
