package com.plumemu.settings.screen

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
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
import com.plumemu.update.UpdateStep
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
 * The update panel's notes, measured: bullets' dots start on the headings' line, their text hangs one
 * indent further in (a long item wraps under its text, not under the dot), the notes stay above the
 * Update button, and the progress bar sits above the status line while downloading.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-mdpi")
class SettingsUpdateNotesAlignTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val longItem = "A very long note that goes on and on, to show that it wraps under its own text and never under the dot, with words that make the lines long: development, recognition, configuration."
    private val release =
        UpdateManifest(
            41, "0.0.3-alpha", "https://plumemu-app.stevensdesk.com/update/p.apk", 9_800_000, "ab".repeat(32), 23, "2026-10-23", emptyList(), null,
            listOf(NoteSection(SectionKind.New, listOf(longItem, "Short.")), NoteSection(SectionKind.Fixed, listOf("Fixed."))),
        )
    private val focus = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.About, rememberedOption = mapOf(SettingsSection.About to 1), page = PanelPage.Update, panelIndex = 0)

    private fun show(step: UpdateStep) =
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                SettingsAt(focus, content = SettingsContent(update = UpdateView(LastCheck(1, CheckOutcome.Found), release, step = step)))
            }
        }

    private fun bounds(tag: String): DpRect = compose.onAllNodesWithTag(tag, useUnmergedTree = true).onFirst().getUnclippedBoundsInRoot()

    @Test
    fun dotsStartOnTheHeadingLineAndTheTextHangsOneIndentIn() {
        show(UpdateStep.Idle)
        val heading = bounds(SettingsTags.UPDATE_HEADING).left
        assertEquals(heading.value, bounds(SettingsTags.UPDATE_DOT).left.value, 0.5f)
        val textLeft = compose.onAllNodes(hasText(longItem), useUnmergedTree = true).onFirst().getUnclippedBoundsInRoot().left
        assertEquals((heading + PlumDimens.settingsNoteIndent).value, textLeft.value, 0.5f)
        // The long item wraps: its note is taller than a short one's single line.
        val note = bounds(SettingsTags.UPDATE_NOTE)
        assertTrue("long note should wrap, height ${note.bottom - note.top}", (note.bottom - note.top).value > 40f)
    }

    @Test
    fun theNotesStayAboveTheUpdateButton() {
        show(UpdateStep.Idle)
        val notes = compose.onNodeWithTag(SettingsTags.UPDATE_NOTES, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val button = compose.onNodeWithTag(SettingsTags.panelItem(0), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("notes end ${notes.bottom} before button ${button.top}", notes.bottom <= button.top)
    }

    @Test
    fun theBarSitsAboveTheDownloadingLineOnTheSameEdge() {
        show(UpdateStep.Downloading(3_400_000, 9_800_000))
        val bar = bounds(SettingsTags.UPDATE_PROGRESS)
        val line = bounds(SettingsPanelTags.STATUS)
        assertTrue("bar ${bar.bottom} above line ${line.top}", bar.bottom <= line.top)
        assertEquals(line.left.value, bar.left.value, 0.5f)
        assertTrue("bar reaches at least the line's end", bar.right.value >= line.right.value - 0.5f)
    }

    @Test
    fun theTitleShowsBuildNumbersOnlyWhenBothNamesAreTheSame() {
        val offered = release.copy(versionName = "0.1.1", versionCode = 950)
        assertEquals("0.1.0" to "0.1.1", titleVersions("0.1.0 (412)", offered))
        assertEquals("0.1.1 (412)" to "0.1.1 (950)", titleVersions("0.1.1 (412)", offered))
    }
}
