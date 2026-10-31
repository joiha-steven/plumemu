package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.account.AccountShots
import com.plumemu.account.AccountStatus
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
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
 * The ScreenScraper rows sit in Library's column like every other row, measured: each row starts
 * and ends where ROM folders does, its label starts on the same line, rows are one gap apart; in
 * the sign-in panel the fields and buttons start on the panel's edge and the title, the field
 * labels and the message on the row-text line 14 in, like a list row's text,
 * and the title's top is the panel's top, as in every other panel. At all six text sizes.
 */
abstract class SettingsAccountAlignBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun labelLeft(
        option: SettingsOption,
        label: String,
    ): Float =
        compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(SettingsTags.option(option))), useUnmergedTree = true)
            .getUnclippedBoundsInRoot().left.value

    protected fun rowsShareTheColumn(label: String) {
        var size by mutableStateOf(TextSize.Small)
        val content = AccountShotContent.of(AccountStatus.SignedIn("plumtester", isRefused = true))
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                // The sections column focused: every Library row is drawn alike (no fill, no dot).
                SettingsAt(SettingsFocus(column = SettingsColumn.Sections, section = SettingsSection.Library), content = content)
            }
        }
        val labels =
            mapOf(
                SettingsOption.RomFolders to "ROM folders",
                SettingsOption.SortGames to "Sort games",
                SettingsOption.SystemOrder to "System order",
                SettingsOption.ScraperAccount to "ScreenScraper",
                SettingsOption.BoxArtRegion to "Box art region",
            )
        val problems = mutableListOf<String>()
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            val at = "$label ${each.name}"
            val reference = bounds(SettingsTags.option(SettingsOption.RomFolders))
            val referenceLabel = labelLeft(SettingsOption.RomFolders, "ROM folders")
            for ((option, text) in labels) {
                val row = bounds(SettingsTags.option(option))
                if (!near(row.left.value, reference.left.value) || !near(row.right.value, reference.right.value)) {
                    problems += "$at: $option row ${row.left}..${row.right}, ROM folders ${reference.left}..${reference.right}"
                }
                val left = labelLeft(option, text)
                if (!near(left, referenceLabel)) problems += "$at: $option label at $left, ROM folders' at $referenceLabel"
            }
            // System order sits between Sort games and the account; Systems is listed only with systems, which this library has not.
            val order = listOf(SettingsOption.SortGames, SettingsOption.SystemOrder, SettingsOption.ScraperAccount, SettingsOption.BoxArtRegion)
            order.zipWithNext().forEach { (above, below) ->
                val gap = bounds(SettingsTags.option(below)).top.value - bounds(SettingsTags.option(above)).bottom.value
                if (!near(gap, PlumDimens.listRowGap.value)) problems += "$at: gap $above to $below is $gap"
            }
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    protected fun formLinesUp(label: String) {
        var size by mutableStateOf(TextSize.Small)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { SignInFormAt(AccountShots.State.Wrong) } }
        val problems = mutableListOf<String>()
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            val at = "$label ${each.name}"
            val title = compose.onNode(hasText("ScreenScraper") and hasAnyAncestor(hasTestTag(SettingsTags.ACCOUNT_PANEL)), useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            val field = bounds(SignInTags.USERNAME)
            val signIn = bounds(SignInTags.item(SignInItem.SignIn))
            val message = bounds(SignInTags.MESSAGE)
            val fieldText = compose.onNode(hasText("plumtester") and hasAnyAncestor(hasTestTag(SignInTags.USERNAME)), useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            val panel = bounds(SettingsTags.PANEL)
            if (!near(field.left.value, panel.left.value)) problems += "$at: field at ${field.left}, the panel at ${panel.left}"
            if (!near(signIn.left.value, panel.left.value)) problems += "$at: Sign in at ${signIn.left}, the panel at ${panel.left}"
            if (!near(title.left.value - panel.left.value, PlumDimens.gameRowPadding.value)) problems += "$at: title at ${title.left}, not on the row-text line"
            if (!near(title.top.value, panel.top.value)) problems += "$at: title's top at ${title.top}, the panel's at ${panel.top}"
            val label = compose.onNode(hasText("Username") and hasAnyAncestor(hasTestTag(SignInTags.USERNAME)), useUnmergedTree = true).getUnclippedBoundsInRoot()
            val inset = label.left.value - field.left.value
            if (!near(inset, PlumDimens.gameRowPadding.value)) problems += "$at: field label $inset in"
            if (fieldText.left.value <= label.right.value) problems += "$at: the name runs into its label"
            if (fieldText.right.value > field.right.value + TOLERANCE) problems += "$at: the name runs past its row"
            val usernameText = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(SignInTags.USERNAME)), useUnmergedTree = true).getUnclippedBoundsInRoot()
            val passwordText = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(SignInTags.PASSWORD)), useUnmergedTree = true).getUnclippedBoundsInRoot()
            if (!near(passwordText.left.value, usernameText.left.value)) problems += "$at: password starts at ${passwordText.left}, the name at ${usernameText.left}"
            if (!near(message.left.value - field.left.value, PlumDimens.gameRowPadding.value)) problems += "$at: message at ${message.left}"
            if (signIn.right.value > panel.right.value + TOLERANCE) problems += "$at: Sign in ends at ${signIn.right}, past the panel's ${panel.right}"
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private fun near(
        a: Float,
        b: Float,
    ) = kotlin.math.abs(a - b) <= TOLERANCE

    private companion object {
        const val TOLERANCE = 0.5f
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsAccountAlign768Test : SettingsAccountAlignBase() {
    @Test fun rowsShareLibrarysColumn() = rowsShareTheColumn("768x432")

    @Test fun theFormLinesUpInThePanel() = formLinesUp("768x432")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsAccountAlign576Test : SettingsAccountAlignBase() {
    @Test fun rowsShareLibrarysColumn() = rowsShareTheColumn("576x432")

    @Test fun theFormLinesUpInThePanel() = formLinesUp("576x432")
}
