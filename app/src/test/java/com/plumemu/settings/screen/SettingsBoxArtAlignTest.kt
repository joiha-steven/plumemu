package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.account.UtcTimeZone
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
 * Box art's panel pages measured at all six text sizes: the account row's value ends
 * where the other values end; in the panel the title, "Signed in as", the status line and the
 * facts start on one line, the row-text line 14 in from the panel's edge, every button starts on
 * the panel's edge and the buttons are one width; the confirm likewise; in the systems picker every
 * row and both buttons share the panel's left edge and one right line, and the names start on one
 * line (one left edge for side-panel content, as in the game panel).
 */
abstract class SettingsBoxArtAlignBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @get:Rule
    val utc = UtcTimeZone()

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun textBounds(
        text: String,
        under: SemanticsMatcher,
    ): DpRect = compose.onNode(hasText(text) and hasAnyAncestor(under), useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun eachSize(
        label: String,
        focus: SettingsFocus,
        content: SettingsContent,
        check: (at: String, problems: MutableList<String>) -> Unit,
    ) {
        var size by mutableStateOf(TextSize.Small)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { SettingsAt(focus, content = content) } }
        val problems = mutableListOf<String>()
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            check("$label ${each.name}", problems)
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private fun focus(
        content: SettingsContent,
        page: PanelPage?,
    ) = AccountShotContent.focusOn(content, SettingsOption.ScraperAccount, page)

    protected fun theRowsValueEndsWithTheOthers(label: String) {
        val content = BoxArtShots.content(BoxArtShots.SIGNED_IN, BoxArtShots.running)
        // The sections column focused: no row has the fill, every value is drawn alike.
        eachSize(label, SettingsFocus(column = SettingsColumn.Sections, section = SettingsSection.Library), content) { at, problems ->
            val value = textBounds("340\u00A0of\u00A02,054\u00A0»", hasTestTag(SettingsTags.option(SettingsOption.ScraperAccount)))
            val folders = textBounds("2 folders\u00A0»", hasTestTag(SettingsTags.option(SettingsOption.RomFolders)))
            if (!near(value.right.value, folders.right.value)) problems += "$at: the account's value ends at ${value.right}, ROM folders' at ${folders.right}"
        }
    }

    protected fun thePanelLinesUp(label: String) {
        val content = BoxArtShots.content(BoxArtShots.SIGNED_IN, BoxArtShots.running).copy(accountLimits = BoxArtShots.limits, boxArtGivenUp = 12)
        eachSize(label, focus(content, PanelPage.ScraperAccount), content) { at, problems ->
            val panel = hasTestTag(SettingsTags.ACCOUNT_PANEL)
            val title = textBounds("ScreenScraper", panel)
            val lines =
                listOf(
                    "Signed in as plumtester",
                    "Downloading box art 340\u00A0of\u00A02,054 · about 5\u00A0h left",
                    "12 games gave up after server errors · Re-download all to try again",
                    "Box art:\u00A0312 MB",
                    "Requests today: 1,234\u00A0of\u00A010,000",
                    "Parallel downloads: 1",
                    "Download speed: 128\u00A0KB/s",
                ).map { it to textBounds(it, panel) }
            val facts = bounds(SettingsPanelTags.FACTS)
            val status = bounds(SettingsPanelTags.STATUS)
            if (facts.top.value < status.bottom.value - TOLERANCE) problems += "$at: the facts start at ${facts.top}, above the status line's end ${status.bottom}"
            if (facts.right.value > bounds(SettingsTags.PANEL).right.value + TOLERANCE) problems += "$at: the facts run past the panel"
            for ((text, line) in lines) if (!near(line.left.value, title.left.value)) problems += "$at: \"$text\" at ${line.left}, the title at ${title.left}"
            val buttons = accountPageItems(content).indices.map { bounds(SettingsTags.panelItem(it)) }
            val edge = bounds(SettingsTags.PANEL).left.value
            if (!near(title.left.value - edge, PlumDimens.gameRowPadding.value)) problems += "$at: the title at ${title.left}, not on the row-text line"
            buttons.forEachIndexed { index, button ->
                if (!near(button.left.value, edge)) problems += "$at: button $index at ${button.left}, the panel's edge at $edge"
                if (!near(button.right.value, buttons.first().right.value)) problems += "$at: button $index ends at ${button.right}, the first at ${buttons.first().right}"
            }
            buttons.zipWithNext().map { (a, b) -> b.top.value - a.bottom.value }.distinct().let { gaps ->
                if (gaps.any { !near(it, gaps.first()) }) problems += "$at: uneven gaps between buttons $gaps"
            }
            if (buttons.first().right.value > bounds(SettingsTags.PANEL).right.value + TOLERANCE) problems += "$at: buttons run past the panel"
        }
    }

    protected fun theConfirmLinesUp(label: String) {
        val content = BoxArtShots.content(BoxArtShots.SIGNED_IN, BoxArtShots.running)
        eachSize(label, focus(content, PanelPage.ScraperConfirm).copy(panelIndex = BoxArtConfirmItem.Cancel.ordinal), content) { at, problems ->
            val panel = hasTestTag(SettingsTags.ACCOUNT_PANEL)
            val title = textBounds("Re-download box art", panel)
            val question = bounds(SettingsPanelTags.STATUS)
            val redownload = bounds(SettingsTags.panelItem(BoxArtConfirmItem.Redownload.ordinal))
            val cancel = bounds(SettingsTags.panelItem(BoxArtConfirmItem.Cancel.ordinal))
            if (!near(question.left.value, title.left.value)) problems += "$at: the question at ${question.left}, the title at ${title.left}"
            val edge = bounds(SettingsTags.PANEL).left.value
            if (!near(title.left.value - edge, PlumDimens.gameRowPadding.value)) problems += "$at: the title at ${title.left}, not on the row-text line"
            for (button in listOf(redownload, cancel)) if (!near(button.left.value, edge)) problems += "$at: a button at ${button.left}, the panel's edge at $edge"
            if (!near(redownload.right.value, cancel.right.value)) problems += "$at: Re-download ends at ${redownload.right}, Cancel at ${cancel.right}"
        }
    }

    protected fun theClearConfirmLinesUp(label: String) {
        val content = BoxArtShots.content(BoxArtShots.SIGNED_IN, BoxArtShots.running)
        eachSize(label, focus(content, PanelPage.ScraperClearConfirm).copy(panelIndex = ClearConfirmItem.Cancel.ordinal), content) { at, problems ->
            val panel = hasTestTag(SettingsTags.ACCOUNT_PANEL)
            val title = textBounds("Clear box art?", panel)
            val body = bounds(SettingsPanelTags.STATUS)
            val clear = bounds(SettingsTags.panelItem(ClearConfirmItem.Clear.ordinal))
            val cancel = bounds(SettingsTags.panelItem(ClearConfirmItem.Cancel.ordinal))
            if (!near(body.left.value, title.left.value)) problems += "$at: the body at ${body.left}, the title at ${title.left}"
            val edge = bounds(SettingsTags.PANEL).left.value
            if (!near(title.left.value - edge, PlumDimens.gameRowPadding.value)) problems += "$at: the title at ${title.left}, not on the row-text line"
            for (button in listOf(clear, cancel)) if (!near(button.left.value, edge)) problems += "$at: a button at ${button.left}, the panel's edge at $edge"
            if (!near(clear.right.value, cancel.right.value)) problems += "$at: Clear ends at ${clear.right}, Cancel at ${cancel.right}"
            if (cancel.top.value < clear.bottom.value) problems += "$at: the buttons overlap"
        }
    }

    protected fun theSignOutConfirmLinesUp(label: String) {
        val content = BoxArtShots.content(BoxArtShots.SIGNED_IN, BoxArtShots.running)
        eachSize(label, focus(content, PanelPage.ScraperSignOutConfirm).copy(panelIndex = SignOutConfirmItem.Cancel.ordinal), content) { at, problems ->
            val panel = hasTestTag(SettingsTags.ACCOUNT_PANEL)
            val title = textBounds("Sign out of ScreenScraper?", panel)
            val question = bounds(SettingsPanelTags.STATUS)
            val signOut = bounds(SettingsTags.panelItem(SignOutConfirmItem.SignOut.ordinal))
            val cancel = bounds(SettingsTags.panelItem(SignOutConfirmItem.Cancel.ordinal))
            if (!near(question.left.value, title.left.value)) problems += "$at: the question at ${question.left}, the title at ${title.left}"
            val edge = bounds(SettingsTags.PANEL).left.value
            if (!near(title.left.value - edge, PlumDimens.gameRowPadding.value)) problems += "$at: the title at ${title.left}, not on the row-text line"
            for (button in listOf(signOut, cancel)) if (!near(button.left.value, edge)) problems += "$at: a button at ${button.left}, the panel's edge at $edge"
            if (!near(signOut.right.value, cancel.right.value)) problems += "$at: Sign out ends at ${signOut.right}, Cancel at ${cancel.right}"
        }
    }

    protected fun thePickerLinesUp(label: String) {
        val content = BoxArtShots.longSystems
        // Focused on the first action: every system row is drawn alike.
        val at = content.systems.size
        val picker = focus(content, PanelPage.ScraperSystems).copy(panelIndex = at, chosenSystems = setOf("nes", "pce"))
        eachSize(label, picker, content) { size, problems ->
            val title = textBounds("Choose systems", hasTestTag(SettingsTags.ACCOUNT_PANEL))
            val first = bounds(SettingsTags.panelItem(0))
            for (index in content.systems.indices + listOf(at, at + 1)) {
                val item = bounds(SettingsTags.panelItem(index))
                val edge = bounds(SettingsTags.PANEL).left.value
                if (!near(item.left.value, edge) || !near(item.right.value, first.right.value)) {
                    problems += "$size: item $index spans ${item.left}..${item.right}, the panel's edge is $edge, the first row ends at ${first.right}"
                }
            }
            if (first.right.value > bounds(SettingsTags.PANEL).right.value + TOLERANCE) problems += "$size: the rows run past the panel"
            // Unclipped: rows scrolled out of the panel count too.
            val lefts =
                content.systems.map { system ->
                    compose.onNode(hasText(system.name) and hasAnyAncestor(hasTestTag(SettingsTags.BOX_ART_SYSTEMS)), useUnmergedTree = true)
                        .getUnclippedBoundsInRoot().left.value
                }
            if (lefts.any { !near(it, lefts.first()) }) problems += "$size: the names start at $lefts"
            if (!near(lefts.first(), title.left.value)) problems += "$size: the names start at ${lefts.first()}, the title at ${title.left}"
        }
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
class SettingsBoxArtAlign768Test : SettingsBoxArtAlignBase() {
    @Test fun theRowsValueEndsWithTheOthers() = theRowsValueEndsWithTheOthers("768x432")

    @Test fun thePanelLinesUp() = thePanelLinesUp("768x432")

    @Test fun theConfirmLinesUp() = theConfirmLinesUp("768x432")

    @Test fun theSignOutConfirmLinesUp() = theSignOutConfirmLinesUp("768x432")

    @Test fun theClearConfirmLinesUp() = theClearConfirmLinesUp("768x432")

    @Test fun thePickerLinesUp() = thePickerLinesUp("768x432")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsBoxArtAlign576Test : SettingsBoxArtAlignBase() {
    @Test fun theRowsValueEndsWithTheOthers() = theRowsValueEndsWithTheOthers("576x432")

    @Test fun thePanelLinesUp() = thePanelLinesUp("576x432")

    @Test fun theConfirmLinesUp() = theConfirmLinesUp("576x432")

    @Test fun theSignOutConfirmLinesUp() = theSignOutConfirmLinesUp("576x432")

    @Test fun theClearConfirmLinesUp() = theClearConfirmLinesUp("576x432")

    @Test fun thePickerLinesUp() = thePickerLinesUp("576x432")
}
