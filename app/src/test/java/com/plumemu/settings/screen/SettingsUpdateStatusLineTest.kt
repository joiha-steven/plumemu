package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.textsize.notFullyShown
import com.plumemu.update.CheckOutcome
import com.plumemu.update.LastCheck
import com.plumemu.update.UpdateFailure
import com.plumemu.update.UpdateManifest
import com.plumemu.update.UpdateStep
import com.plumemu.update.UpdateView
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.text.DateFormat
import java.util.Date

/**
 * The Updates panel while an update is found: a finished Check now always answers with when it ran, on the screen with Check now
 * focused, without the words "Update found" that would repeat the title.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-mdpi")
class SettingsUpdateStatusLineTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val release = UpdateManifest(41, "0.0.3-alpha", "https://plumemu-app.stevensdesk.com/update/p.apk", 9_800_000, "ab".repeat(32), 23, "2026-10-23", listOf("One."), null)
    private val checkedAt = System.currentTimeMillis()
    private val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(checkedAt))
    private val found = UpdateView(lastCheck = LastCheck(checkedAt, CheckOutcome.Found), found = release)

    private fun focusOn(item: UpdatePanelItem, view: UpdateView) =
        SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.About, rememberedOption = mapOf(SettingsSection.About to 1), page = PanelPage.Update, panelIndex = updatesPanelItems(view).indexOf(item))

    private fun lines(needle: String) = compose.onAllNodesWithText(needle, substring = true, useUnmergedTree = true).fetchSemanticsNodes()

    /** Up to Check now by the keys while it checks, as the model keeps the cursor on it, then the answer. */
    @Test
    fun checkNowAnswersWithWhenItRanAndNotWithTheWordsUpdateFound() {
        var view by mutableStateOf(found)
        var focus by mutableStateOf(focusOn(UpdatePanelItem.CheckNow, found))
        compose.setContent { SettingsAt(focus, content = SettingsContent(update = view)) }
        compose.waitForIdle()
        view = found.copy(isChecking = true)
        focus = focusOn(UpdatePanelItem.CheckNow, view)
        compose.waitForIdle()
        assertTrue("it says it is checking", lines("Checking for updates").isNotEmpty())
        view = found
        focus = focusOn(UpdatePanelItem.CheckNow, view)
        compose.waitForIdle()
        val line = lines(time).singleOrNull { it.config[SemanticsProperties.Text].single().text.startsWith("Last checked") }
        assertTrue("no line says when the check ran", line != null)
        assertNull("on the screen with Check now focused", notFullyShown(line!!, compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()))
        assertTrue("the title already says it", lines("is available").isEmpty())
    }

    @Test
    fun aStepLeftStandingKeepsTheAnswerUnderIt() {
        val failed = found.copy(step = UpdateStep.Failed(UpdateFailure.Offline))
        compose.setContent { SettingsAt(focusOn(UpdatePanelItem.Update, failed), content = SettingsContent(update = failed)) }
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsPanelTags.STATUS, useUnmergedTree = true).assertIsDisplayed()
        assertTrue("no line says when the check ran", lines("Last checked").size == 1)
    }

    @Test
    fun aDownloadSaysOnlyItsStep() {
        val downloading = found.copy(step = UpdateStep.Downloading(1, 2))
        compose.setContent { SettingsAt(focusOn(UpdatePanelItem.Cancel, downloading), content = SettingsContent(update = downloading)) }
        compose.waitForIdle()
        assertTrue(lines("Last checked").isEmpty())
    }
}
