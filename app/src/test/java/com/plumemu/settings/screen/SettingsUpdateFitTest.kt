package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.update.CheckOutcome
import com.plumemu.update.LastCheck
import com.plumemu.update.NoteSection
import com.plumemu.update.SectionKind
import com.plumemu.update.UpdateManifest
import com.plumemu.update.UpdateStep
import com.plumemu.update.UpdateView
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The update panel at the big text sizes in French, 576x432: the notes give way first, so every
 * button of every step stays inside the panel's frame (the frame itself does not scroll).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-w576dp-h432dp-mdpi")
class SettingsUpdateFitTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val item = "A long note that goes on for a while so that it fills the notes area and then some more, to be sure the notes have to give way."
    private val release =
        UpdateManifest(
            41, "0.0.3-alpha", "https://plumemu-app.stevensdesk.com/update/p.apk", 9_800_000, "ab".repeat(32), 23, "2026-10-23", emptyList(), null,
            listOf(NoteSection(SectionKind.New, listOf(item, item)), NoteSection(SectionKind.Fixed, listOf(item, item))),
        )
    private val focus = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.About, rememberedOption = mapOf(SettingsSection.About to 1), page = PanelPage.Update, panelIndex = 1)

    @Test
    fun everyButtonStaysInsideThePanelAtTheBigSizes() {
        var size by mutableStateOf(TextSize.Larger)
        var step by mutableStateOf<UpdateStep>(UpdateStep.AskMetered)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                SettingsAt(focus, content = SettingsContent(update = UpdateView(LastCheck(1, CheckOutcome.Found), release, step = step)))
            }
        }
        val problems = mutableListOf<String>()
        for (each in listOf(TextSize.Larger, TextSize.Huge, TextSize.Largest)) {
            for (next in listOf(UpdateStep.AskMetered, UpdateStep.NeedsPermission)) {
                size = each
                step = next
                compose.waitForIdle()
                val panel = compose.onNodeWithTag(SettingsTags.UPDATE_PANEL, useUnmergedTree = true).getUnclippedBoundsInRoot()
                for (index in 0..1) {
                    val button = compose.onNodeWithTag(SettingsTags.panelItem(index), useUnmergedTree = true).getUnclippedBoundsInRoot()
                    if (button.bottom > panel.bottom || button.top < panel.top) problems += "$each $next button $index ${button.top}..${button.bottom} outside ${panel.top}..${panel.bottom}"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
