package com.plumemu.settings.screen

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Library's rows with a count say it in the value on the right, like "1 folder" and "11 shown":
 * a count in the label wrapped "Unidentified games (10,000)" to two lines when focused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsCountRowsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val content =
        ShotContent.library.copy(
            unidentifiedGames = (1..12_345L).map { SettingsGame(it, "Game $it", "Super Nintendo") },
            hiddenGames = listOf(SettingsGame(1, "Chrono Trigger", "Super Nintendo"), SettingsGame(2, "Controller Test", "Super Nintendo")),
        )

    private fun check(option: SettingsOption, label: String, count: String) {
        compose.setContent { PlumTheme { SettingsAt(optionsAt(option, content), content = content) } }
        compose.onAllNodesWithText(label, useUnmergedTree = true).assertCountEquals(2) // the row and its panel's title
        compose.onAllNodes(hasText(count) and hasAnyAncestor(hasTestTag(SettingsTags.value(option))), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun unidentifiedGamesCountIsTheRowsValue() = check(SettingsOption.UnidentifiedGames, "Unidentified games", "12,345")

    @Test
    fun hiddenGamesCountIsTheRowsValue() = check(SettingsOption.HiddenGames, "Hidden games", "2")
}
