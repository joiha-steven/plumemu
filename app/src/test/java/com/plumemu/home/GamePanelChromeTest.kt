package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreui.components.HINT_KEY_CAP_TAG
import com.plumemu.coreui.components.PANEL_TITLE_DOT_TAG
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.sampleFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The game panel's chrome (decision 0011, point 5) in numbers: the drawn shadow stays 8 dp or more above the hint keys,
 * and the title's dot follows the title's visible end without moving or squeezing the title, at every text size.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GamePanelChromeTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var size by mutableStateOf(TextSize.Medium)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun show(title: String) =
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default.copy(textSize = size)) {
                HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = OpenPanel(GameRef("snes-6", title), PanelScreen.Menu(sampleFacts(title = title))))
            }
        }

    private fun assertShadowClearOfHints() {
        show("Super Metroid")
        val panel = bounds(GamePanelTags.PANEL)
        val caps = compose.onAllNodesWithTag(HINT_KEY_CAP_TAG, useUnmergedTree = true)
        val hintTop = (0 until caps.fetchSemanticsNodes().size).minOf { caps[it].getUnclippedBoundsInRoot().top.value }
        val shadowBottom = panel.bottom.value + PlumDimens.panelOutsetBottom.value + PlumDimens.panelShadowBottom.value
        assertTrue("shadow ends at $shadowBottom, hint keys start at $hintTop: need 8 dp clear", hintTop - shadowBottom >= 8f)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun theShadowIsClearOfTheHintKeysAt768() = assertShadowClearOfHints()

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun theShadowIsClearOfTheHintKeysAt576() = assertShadowClearOfHints()

    private fun assertDotFollowsTitle(what: String, oneLine: Boolean) {
        val panel = bounds(GamePanelTags.PANEL)
        val text = bounds(GamePanelTags.TITLE)
        val dot = bounds(PANEL_TITLE_DOT_TAG)
        assertEquals("$what: title starts 14 in", panel.left.value + 14f, text.left.value, 0.5f)
        if (oneLine) {
            assertEquals("$what: dot after the title's visible end", text.right.value + PlumDimens.panelTitleDotGap.value, dot.left.value, 0.5f)
        } else {
            // A cut title's last line may end before the box's right edge; the exact place is PanelTitleDotTest's.
            assertTrue("$what: dot on the last line", dot.left.value - PlumDimens.panelTitleDotGap.value <= text.right.value + 0.5f && dot.top.value >= (text.top.value + text.bottom.value) / 2f - 0.5f)
        }
        assertTrue("$what: dot inside the text column", dot.right.value <= panel.right.value - 14f + 0.5f)
        assertTrue("$what: dot beside the title's lines", dot.top.value >= text.top.value - 0.5f && dot.bottom.value <= text.bottom.value + 0.5f)
    }

    private fun allSizes(title: String, oneLine: Boolean) {
        show(title)
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            assertDotFollowsTitle("$title at $each", oneLine)
        }
    }

    private val long = "The Legend of Zelda: A Link to the Past and Four Swords Collector's Edition Extra Long Title"

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun theDotFollowsAShortTitleAtEveryTextSizeAt768() = allSizes("Super Metroid", oneLine = true)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun theDotFollowsALongCutTitleAtEveryTextSizeAt768() = allSizes(long, oneLine = false)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun theDotFollowsAShortTitleAtEveryTextSizeAt576() = allSizes("Super Metroid", oneLine = true)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun theDotFollowsALongCutTitleAtEveryTextSizeAt576() = allSizes(long, oneLine = false)
}
