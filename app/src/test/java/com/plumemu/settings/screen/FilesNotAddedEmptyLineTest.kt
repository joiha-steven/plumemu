package com.plumemu.settings.screen

import androidx.compose.ui.test.onAllNodesWithText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Library › Files not added with nothing to list: with no games at all it must not claim that every file is in the library. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class FilesNotAddedEmptyLineTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(content: SettingsContent) {
        val at = SettingsSection.Library.rowsIn(content).indexOf(SettingsOption.FilesNotAdded)
        val focus =
            SettingsFocus(
                column = SettingsColumn.Panel,
                section = SettingsSection.Library,
                rememberedOption = mapOf(SettingsSection.Library to at),
                page = PanelPage.FilesNotAdded,
            )
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { SettingsAt(focus, settingsForShot(), content) } }
        compose.waitForIdle()
    }

    private fun hasText(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun noGamesAndNothingLeftOutSaysThereAreNoFilesToShow() {
        show(ShotContent.library.copy(gameCount = 0, systemCount = 0, notAdded = emptyList()))
        assertTrue(hasText("No files to show"))
        assertFalse(hasText("Every file is in the library"))
    }

    @Test
    fun gamesAndNothingLeftOutKeepsTheOldLine() {
        show(ShotContent.library.copy(notAdded = emptyList()))
        assertTrue(hasText("Every file is in the library"))
        assertFalse(hasText("No files to show"))
    }
}
