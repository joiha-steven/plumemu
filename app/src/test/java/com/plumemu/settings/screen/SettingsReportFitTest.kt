package com.plumemu.settings.screen

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.report.ProblemReportForm
import com.plumemu.report.ReportItem
import com.plumemu.report.ReportShots
import com.plumemu.report.ReportTags
import com.plumemu.keyboard.KeyboardChoice
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The report form scrolls: with the focus moved down to Cancel, Cancel (and Send, and the sends
 * line) lie whole inside the panel (measured unclipped: a clipped bound would always look inside), at 768x432 and 576x432. Reference shots are taken before the
 * focus scrolls anything, so they can show Cancel cut at the panel's edge; this checks it comes into view.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
open class SettingsReportFitTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun check(form: ProblemReportForm) {
        compose.setContent { PlumTheme { ReportSettingsWith(form) } }
        compose.waitForIdle()
        while (form.focus != ReportItem.Leave) {
            compose.runOnIdle { form.onAction(Action.Move(Direction.Down)) }
            compose.waitForIdle()
        }
        val panel = compose.onNodeWithTag(SettingsTags.ACCOUNT_PANEL, useUnmergedTree = true).getUnclippedBoundsInRoot()
        for (tag in listOf(ReportTags.item(ReportItem.Leave), ReportTags.item(ReportItem.Send))) {
            val b = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("$tag bottom ${b.bottom} past the panel's ${panel.bottom}", b.bottom <= panel.bottom)
            assertTrue("$tag top ${b.top} above the panel's ${panel.top}", b.top >= panel.top)
        }
    }

    @Test
    fun cancelIsWholeInTheEmptyForm() = check(ProblemReportForm(KeyboardChoice.Plumemu))

    @Test
    fun cancelIsWholeAfterAnEmptySend() = check(ReportShots.form(ReportShots.State.NoText))

    @Test
    fun cancelIsWholeAfterARefusal() = check(ReportShots.form(ReportShots.State.Refused))
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsReportFitNarrowTest : SettingsReportFitTest()
