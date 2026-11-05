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

    private var size by mutableStateOf(TextSize.Small)

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
        val shadowBottom = panel.bottom.value + PlumDimens.panelShadowBottom.value
        assertTrue("shadow ends at $shadowBottom, hint keys start at $hintTop: need 8 dp clear", hintTop - shadowBottom >= 8f)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun theShadowIsClearOfTheHintKeysAt768() = assertShadowClearOfHints()

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun theShadowIsClearOfTheHintKeysAt576() = assertShadowClearOfHints()

    private fun assertTitleHasNoDot(what: String) {
        val panel = bounds(GamePanelTags.PANEL)
        val text = bounds(GamePanelTags.TITLE)
        assertEquals("$what: title starts panelTitleStart in from the rows' start", panel.left.value + PlumDimens.panelTitleStart.value, text.left.value, 0.5f)
        assertTrue("$what: no accent dot after the title (it carried no state)", compose.onAllNodesWithTag(PANEL_TITLE_DOT_TAG, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    private fun allSizes(title: String, oneLine: Boolean) {
        show(title)
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            assertTitleHasNoDot("$title at $each")
        }
    }

    private val long = "The Legend of Zelda: A Link to the Past and Four Swords Collector's Edition Extra Long Title"

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun theTitleHasNoDotAndFitsAShortTitleAtEveryTextSizeAt768() = allSizes("Super Metroid", oneLine = true)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun theTitleHasNoDotAndFitsALongCutTitleAtEveryTextSizeAt768() = allSizes(long, oneLine = false)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun theTitleHasNoDotAndFitsAShortTitleAtEveryTextSizeAt576() = allSizes("Super Metroid", oneLine = true)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun theTitleHasNoDotAndFitsALongCutTitleAtEveryTextSizeAt576() = allSizes(long, oneLine = false)
}
