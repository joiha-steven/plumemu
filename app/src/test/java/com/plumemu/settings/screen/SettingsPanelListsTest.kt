package com.plumemu.settings.screen

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Library panels that list the whole library compose only what fits (a 10,000-game library ran
 * the app out of memory on the low-end emulator when every row was built).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsPanelListsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = List(10_000) { SettingsGame(it + 1L, "Game $it", "Super Nintendo") }
    private val content = ShotContent.library.copy(hiddenGames = games, unidentifiedGames = games)

    private fun rowComposedAt(index: Int) = compose.onAllNodes(hasTestTag(SettingsTags.panelItem(index))).fetchSemanticsNodes().isNotEmpty()

    private fun show(option: SettingsOption, page: PanelPage, entered: Boolean) {
        val base =
            SettingsFocus(
                column = SettingsColumn.Options,
                section = SettingsSection.Library,
                rememberedOption = mapOf(SettingsSection.Library to SettingsSection.Library.rowsIn(content).indexOf(option)),
            )
        val focus = if (entered) base.copy(column = SettingsColumn.Panel, page = page, panelIndex = 0) else base
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) { SettingsAt(focus, content = content) }
        }
    }

    @Test
    fun hiddenGamesPreviewComposesOnlyAScreenful() {
        show(SettingsOption.HiddenGames, PanelPage.HiddenGames, entered = false)
        compose.onNodeWithTag(SettingsTags.PANEL).assertExists()
        assertTrue("first row is there", rowComposedAt(0))
        assertTrue("not row 100", !rowComposedAt(100))
        assertTrue("row 9,999 is not composed", !rowComposedAt(9_999))
    }

    @Test
    fun unidentifiedGamesEnteredComposesOnlyAScreenful() {
        show(SettingsOption.UnidentifiedGames, PanelPage.UnidentifiedGames, entered = true)
        assertTrue("first row is there", rowComposedAt(0))
        assertTrue("not row 100", !rowComposedAt(100))
    }
}
