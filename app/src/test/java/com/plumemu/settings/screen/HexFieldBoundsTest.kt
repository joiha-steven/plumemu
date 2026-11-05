package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.components.FieldTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.report.ReportShots
import com.plumemu.report.ReportItem
import com.plumemu.report.ReportTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hex code typed on Plumemu's tray wears the shared field look (collar and well in one box), at the reference
 * sizes in both themes: the box lies inside the hex row, holds the typed text, and is as high as a field; Report a
 * problem with the tray up shows no button as a strip.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
open class HexFieldBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun eachTheme(content: @Composable () -> Unit, check: (String, MutableList<String>) -> Unit) {
        var settings by mutableStateOf(PlumThemeSettings.Graphite)
        compose.setContent { PlumTheme(settings) { content() } }
        val problems = mutableListOf<String>()
        for ((theme, each) in listOf("Graphite" to PlumThemeSettings.Graphite, "Paper" to PlumThemeSettings.Paper)) {
            settings = each
            compose.waitForIdle()
            check(theme, problems)
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun hexWellLiesInsideItsRow() =
        eachTheme({ HexTypingSettings() }) { theme, problems ->
            val row = compose.onNodeWithTag(SettingsTags.HEX_ROW, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val box = compose.onNode(hasTestTag(FieldTags.BOX) and hasAnyAncestor(hasTestTag(SettingsTags.HEX_ROW)), useUnmergedTree = true).getUnclippedBoundsInRoot()
            val text = compose.onNode(hasTestTag(SettingsTags.HEX_TEXT), useUnmergedTree = true).getUnclippedBoundsInRoot()
            if (box.left < row.left || box.right > row.right || box.top < row.top || box.bottom > row.bottom) problems += "$theme: well $box is not inside the row $row"
            if (text.left < box.left || text.right > box.right || text.top < box.top || text.bottom > box.bottom) problems += "$theme: typed text $text is not inside the well $box"
        }

    @Test
    fun reportButtonsAreNotStripsOverTheTray() =
        eachTheme({ ReportSettings(ReportShots.State.Typing, true) }) { theme, problems ->
            for (item in listOf(ReportItem.Send, ReportItem.Leave)) {
                val nodes = compose.onAllNodes(hasTestTag(ReportTags.item(item)), useUnmergedTree = true)
                if (nodes.fetchSemanticsNodes().isEmpty()) continue
                val shown = nodes[0].visibleShare()
                if (shown > 0.001f && shown < 0.999f) problems += "$theme: $item is ${(shown * 100).toInt()} % visible, a strip"
            }
        }

    private fun SemanticsNodeInteraction.visibleShare(): Float {
        val node = fetchSemanticsNode()
        if (!node.layoutInfo.isPlaced || node.size.height == 0) return 0f
        return (node.boundsInRoot.height / node.size.height).coerceIn(0f, 1f)
    }
}

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HexFieldBoundsNarrowTest : HexFieldBoundsTest()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HexFieldBoundsWideTest : HexFieldBoundsTest()
