package com.plumemu.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
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
 * The folder step's three bullet lines: each bullet's centre sits inside the first line of its text, wrapped or not,
 * at every text size; and the line that tells a gamepad user what to do when the picker's button cannot be reached shows.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SetupFolderPointsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bulletsOnFirstLines(): Int {
        var size by mutableStateOf(TextSize.Small)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { SetupAt(SetupFakes.chooseFolder) } }
        var wrapped = 0
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            val dots = compose.onAllNodesWithTag(SetupTags.POINT_DOT, useUnmergedTree = true).fetchSemanticsNodes()
            val texts = compose.onAllNodesWithTag(SetupTags.POINT_TEXT, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("three points at ${each.name}", dots.size == 3 && texts.size == 3)
            for (i in 0 until 3) {
                val layouts = mutableListOf<TextLayoutResult>()
                texts[i].config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                val layout = layouts.single()
                if (layout.lineCount > 1) wrapped++
                val centre = dots[i].positionInRoot.y + dots[i].size.height / 2f
                val top = texts[i].positionInRoot.y + layout.getLineTop(0)
                val bottom = texts[i].positionInRoot.y + layout.getLineBottom(0)
                val quarter = (bottom - top) / 4f
                assertTrue("${each.name} bullet $i centre $centre is not on the middle of the first line $top..$bottom (${layout.lineCount} lines)", centre in (top + quarter)..(bottom - quarter))
            }
        }
        return wrapped
    }

    @Test @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun bulletsAt576() {
        assertTrue("some line wrapped, or the test proves nothing", bulletsOnFirstLines() > 0)
    }

    @Test @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun bulletsAt768() {
        bulletsOnFirstLines()
    }

    @Test @Config(qualifiers = "vi-w576dp-h432dp-land-xhdpi")
    fun bulletsInVietnameseAt576() {
        bulletsOnFirstLines()
    }

    @Test @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun theGamepadLineAndTheStopsShow() {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { SetupAt(SetupFakes.chooseFolder) } }
        compose.onNodeWithText("Then confirm that folder there. On some handhelds this needs a touch of the screen.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Plumemu sees only the folders you choose.", useUnmergedTree = true).assertExists()
        assertTrue(compose.onAllNodesWithTag(SetupTags.POINT_TEXT, useUnmergedTree = true).fetchSemanticsNodes().size == 3)
    }
}
