package com.plumemu.settings.screen

import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.update.CheckOutcome
import com.plumemu.update.LastCheck
import com.plumemu.update.NoteSection
import com.plumemu.update.SectionKind
import com.plumemu.update.UpdateManifest
import com.plumemu.update.UpdateView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One left edge for side-panel content: a panel's title and text start on the row-text line,
 * [PlumDimens.gameRowPadding] in from the panel's edge, in the right column of three columns on the edge itself (the board), and its
 * buttons start on the edge itself. Measured on an explanation (Push right twice to play), a colour
 * panel (Background), About and the update panel with its Update button.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsPanelTextLineTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val release =
        UpdateManifest(
            versionCode = 41,
            versionName = "0.0.3-alpha",
            apkUrl = "https://example.invalid/plumemu.apk",
            size = 9_800_000,
            sha256 = "ab".repeat(32),
            minSdk = 23,
            released = "2026-10-23",
            notes = listOf("Faster scans."),
            notesUrl = null,
        )
    private val withUpdate = SettingsContent(update = UpdateView(lastCheck = LastCheck(1_768_471_200_000L, CheckOutcome.Found), found = release))

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun textLeft(text: String) =
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(SettingsTags.PANEL)), useUnmergedTree = true).getUnclippedBoundsInRoot().left.value

    @Test
    fun titlesAndTextStartOnTheColumnsLeftEdgeInThreeColumns() = textStartsAt(0f)

    /** Two columns (768 dp at the largest text) keep the row-text line. */
    @Test
    fun titlesAndTextStartOnTheRowTextLineInTwoColumns() = textStartsAt(PlumDimens.gameRowPadding.value, com.plumemu.coreui.theme.TextSize.Largest)

    private fun textStartsAt(
        expected: Float,
        size: com.plumemu.coreui.theme.TextSize = com.plumemu.coreui.theme.TextSize.Small,
    ) {
        var focus by mutableStateOf(optionsAt(SettingsOption.PushRightToPlay))
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { SettingsAt(focus, settingsForShot().let { it.copy(appearance = it.appearance.copy(accent = ColorSetting.Preset("Red"))) }, withUpdate) } }
        val problems = mutableListOf<String>()
        val cases =
            listOf(
                optionsAt(SettingsOption.PushRightToPlay) to listOf("Push right twice to play"),
                optionsAt(SettingsOption.Background) to listOf("Background", "Graphite"),
                // The Accent page names the focused accent under the swatches, like Background and Text color.
                optionsAt(SettingsOption.Accent) to listOf("Red"),
                optionsAt(SettingsOption.Version) to listOf("Plumemu", "Version 0.0.2-alpha"),
                SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.About, rememberedOption = mapOf(SettingsSection.About to 1)) to
                    // A note's text hangs one indent past the line, after its dot (SettingsUpdateNotesAlignTest).
                    listOf("Plumemu 0.0.2-alpha → 0.0.3-alpha"),
            )
        for ((at, texts) in cases) {
            focus = at
            compose.waitForIdle()
            val edge = bounds(SettingsTags.PANEL).left.value
            for (text in texts) {
                val inset = textLeft(text) - edge
                if (kotlin.math.abs(inset - expected) > TOLERANCE) problems += "\"$text\" starts $inset in, not $expected"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun theUpdateNotesHeadingStartsOnTheColumnsLeftEdgeInThreeColumns() {
        val grouped = SettingsContent(update = UpdateView(lastCheck = LastCheck(1, CheckOutcome.Found), found = release.copy(sections = listOf(NoteSection(SectionKind.New, listOf("One."))))))
        val inPanel = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.About, rememberedOption = mapOf(SettingsSection.About to 1), page = PanelPage.Update)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { SettingsAt(inPanel, content = grouped) } }
        val inset = bounds(SettingsTags.UPDATE_HEADING).left.value - bounds(SettingsTags.PANEL).left.value
        assertTrue("heading starts $inset in, not on the column's left edge", kotlin.math.abs(inset) <= TOLERANCE)
    }

    @Test
    fun theUpdateButtonStartsOnThePanelsEdgeLikeSignIn() {
        val inPanel = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.About, rememberedOption = mapOf(SettingsSection.About to 1), page = PanelPage.Update)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { SettingsAt(inPanel, content = withUpdate) } }
        val edge = bounds(SettingsTags.PANEL).left.value
        val update = bounds(SettingsTags.panelItem(0)).left.value
        assertTrue("Update starts at $update, the panel's edge at $edge", kotlin.math.abs(update - edge) <= TOLERANCE)
    }

    /** The colour [text] in the panel is drawn in, read from its text layout. */
    private fun colorOf(text: String): Color {
        val node = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(SettingsTags.PANEL)), useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().layoutInput.style.color
    }

    @Test
    fun theReleasedLineIsQuieterThanTheNotes() {
        val inPanel = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.About, rememberedOption = mapOf(SettingsSection.About to 1), page = PanelPage.Update)
        var colors: com.plumemu.coreui.theme.PlumComposeColors? = null
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                colors = PlumTheme.colors
                SettingsAt(inPanel, content = withUpdate)
            }
        }
        val theme = checkNotNull(colors)
        val released = compose.onNode(hasText("Released", substring = true) and hasAnyAncestor(hasTestTag(SettingsTags.PANEL)), useUnmergedTree = true)
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        assertEquals("the released line is a fact: muted", theme.muted, colorOf(released))
        assertEquals("a note is prose: text2", theme.text2, colorOf("Faster scans."))
    }

    private companion object {
        const val TOLERANCE = 0.5f
    }
}
