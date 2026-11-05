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
import com.plumemu.account.AccountStatus
import com.plumemu.account.RaAccountStatus
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInResult
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
 * The RetroAchievements panel measured at all six text sizes: in the sign-in form the fields and buttons start on the
 * panel's edge, the title, the field labels, the message and the help lines on the row-text line 14 in, the key starts
 * where the user name does, nothing runs past the panel; signed in, the title's top is the panel's top, the lines start on
 * the row-text line and the buttons on the panel's edge.
 */
abstract class SettingsRaAccountAlignBase {
    /** True in the classes of a wide screen, where small sizes show three columns and a panel's title starts on its edge (the board). */
    protected open val wide = false

    private fun titleStart(at: String) = panelTextStartAt(at, wide)

    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun textIn(
        text: String,
        tag: String,
    ): DpRect = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).getUnclippedBoundsInRoot()

    protected fun formLinesUp(label: String) {
        var size by mutableStateOf(TextSize.Small)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { RaSignInFormAt("plumtester", SignInResult.RaWrongKey, checking = false) } }
        val problems = mutableListOf<String>()
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            val at = "$label ${each.name}"
            val panel = bounds(SettingsTags.PANEL)
            val title = textIn("RetroAchievements", SettingsTags.PANEL)
            val field = bounds(SignInTags.USERNAME)
            val key = bounds(SignInTags.PASSWORD)
            val signIn = bounds(SignInTags.item(SignInItem.SignIn))
            val message = bounds(SignInTags.MESSAGE)
            if (!near(field.left.value, panel.left.value)) problems += "$at: field at ${field.left}, the panel at ${panel.left}"
            if (!near(signIn.left.value, panel.left.value)) problems += "$at: Sign in at ${signIn.left}, the panel at ${panel.left}"
            if (!near(title.left.value - panel.left.value, PlumDimens.gameRowPadding.value)) problems += "$at: title at ${title.left}, not on the row-text line"
            if (!near(title.top.value, panel.top.value)) problems += "$at: title's top at ${title.top}, the panel's at ${panel.top}"
            val userLabel = textIn("Username", SignInTags.USERNAME)
            val keyLabel = textIn("Web API key", SignInTags.PASSWORD)
            if (!near(userLabel.left.value - field.left.value, PlumDimens.gameRowPadding.value)) problems += "$at: user label ${userLabel.left.value - field.left.value} in"
            if (!near(keyLabel.left.value - key.left.value, PlumDimens.gameRowPadding.value)) problems += "$at: key label ${keyLabel.left.value - key.left.value} in"
            val userText = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(SignInTags.USERNAME)), useUnmergedTree = true).getUnclippedBoundsInRoot()
            val keyText = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(SignInTags.PASSWORD)), useUnmergedTree = true).getUnclippedBoundsInRoot()
            if (!near(keyText.left.value, userText.left.value)) problems += "$at: key starts at ${keyText.left}, the name at ${userText.left}"
            if (keyText.left.value <= keyLabel.right.value) problems += "$at: the key runs into its label"
            if (!near(message.left.value - field.left.value, PlumDimens.gameRowPadding.value)) problems += "$at: message at ${message.left}"
            for ((name, box) in mapOf("field" to field, "key" to key, "Sign in" to signIn, "message" to message)) {
                if (box.right.value > panel.right.value + TOLERANCE) problems += "$at: $name ends at ${box.right}, past the panel's ${panel.right}"
            }
            for (tag in listOf(SettingsPanelTags.RA_HELP, SettingsPanelTags.RA_PRIVACY)) {
                val note = compose.onAllNodesWithTagUnclipped(tag)
                for (box in note) {
                    if (!near(box.left.value - panel.left.value, PlumDimens.gameRowPadding.value)) problems += "$at: a $tag line at ${box.left}, not on the row-text line"
                    if (box.right.value > panel.right.value + TOLERANCE) problems += "$at: a $tag line ends at ${box.right}, past the panel's ${panel.right}"
                }
            }
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    internal fun signedInLinesUp(
        label: String,
        status: RaAccountStatus.SignedIn,
    ) {
        var size by mutableStateOf(TextSize.Small)
        val content = AccountShotContent.of(AccountStatus.SignedOut).copy(raAccount = status)
        val row = SettingsSection.Accounts.rowsIn(content).indexOf(SettingsOption.RetroAchievementsAccount)
        val focus = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.Accounts, rememberedOption = mapOf(SettingsSection.Accounts to row), page = PanelPage.RaAccount)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { SettingsAt(focus, content = content) } }
        val problems = mutableListOf<String>()
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            val at = "$label ${each.name}"
            val panel = bounds(SettingsTags.PANEL)
            val title = textIn("RetroAchievements", SettingsTags.PANEL)
            val button = bounds(SettingsTags.panelItem(0))
            // At the largest size every page, and at the two largest the refused page, is taller than the panel and scrolls to keep the focused button whole: the title is then above the top by design.
            val scrolls = each.name == "Largest" || (status.isKeyRefused && each.name == "Huge")
            if (!scrolls && !near(title.top.value, panel.top.value)) problems += "$at: title's top at ${title.top}, the panel's at ${panel.top}"
            if (!near(title.left.value - panel.left.value, titleStart(at))) problems += "$at: title at ${title.left}, not on the row-text line"
            if (!status.isKeyRefused) {
                val line = bounds(SettingsPanelTags.STATUS)
                if (!near(line.left.value, title.left.value)) problems += "$at: the signed-in line at ${line.left}, the title at ${title.left}"
                if (line.right.value > panel.right.value + TOLERANCE) problems += "$at: the signed-in line ends at ${line.right}, past the panel's ${panel.right}"
            }
            if (!near(button.left.value, panel.left.value)) problems += "$at: the first button at ${button.left}, the panel at ${panel.left}"
            if (button.right.value > panel.right.value + TOLERANCE) problems += "$at: the first button ends at ${button.right}, past the panel's ${panel.right}"
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagUnclipped(tag: String): List<DpRect> =
        onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().indices.map {
            onAllNodes(hasTestTag(tag), useUnmergedTree = true)[it].getUnclippedBoundsInRoot()
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
class SettingsRaAccountAlign768Test : SettingsRaAccountAlignBase() {
    override val wide = true

    @Test fun theFormLinesUpInThePanel() = formLinesUp("768x432")

    @Test fun theSignedInPageLinesUp() = signedInLinesUp("768x432", RaAccountStatus.SignedIn("plumtester", 1234))

    @Test fun theRefusedPageLinesUp() = signedInLinesUp("768x432", RaAccountStatus.SignedIn("plumtester", 1234, isKeyRefused = true))
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsRaAccountAlign576Test : SettingsRaAccountAlignBase() {
    @Test fun theFormLinesUpInThePanel() = formLinesUp("576x432")

    @Test fun theSignedInPageLinesUp() = signedInLinesUp("576x432", RaAccountStatus.SignedIn("plumtester", 1234))

    @Test fun theRefusedPageLinesUp() = signedInLinesUp("576x432", RaAccountStatus.SignedIn("plumtester", 1234, isKeyRefused = true))
}
