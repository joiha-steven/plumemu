package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import com.plumemu.account.AccountStatus
import com.plumemu.account.RaAccountStatus
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A row's label is never broken inside a word to make room for its value: at 768 x 432 "RetroAchievements" came out as
 * "RetroAchieve / ments" beside "Signed in as plumtester". The value gives way (it may wrap and is cut), not the word.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsLongWordLabelTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun theRetroAchievementsLabelStaysOnOneLine() {
        var size by mutableStateOf(TextSize.Small)
        val content = AccountShotContent.of(AccountStatus.SignedOut).copy(raAccount = RaAccountStatus.SignedIn("plumtester", 1234))
        val row = SettingsSection.Accounts.rowsIn(content).indexOf(SettingsOption.RetroAchievementsAccount)
        val focus =
            SettingsFocus(
                column = SettingsColumn.Options,
                section = SettingsSection.Accounts,
                rememberedOption = mapOf(SettingsSection.Accounts to row),
            )
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { SettingsAt(focus, content = content) } }
        val problems = mutableListOf<String>()
        for (each in listOf(TextSize.Small, TextSize.Medium, TextSize.Large)) {
            size = each
            compose.waitForIdle()
            val label =
                compose.onAllNodesWithText("RetroAchievements", useUnmergedTree = true).fetchSemanticsNodes().indices
                    .map { compose.onAllNodesWithText("RetroAchievements", useUnmergedTree = true)[it].getUnclippedBoundsInRoot() }
                    .minBy { it.left }
            if (label.bottom - label.top > ONE_LINE * each.ordinal.plus(10) / 10) problems += "$each: the label is ${label.bottom - label.top} tall"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        val ONE_LINE = 28.dp
    }
}
