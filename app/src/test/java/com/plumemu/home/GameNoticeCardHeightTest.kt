package com.plumemu.home

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.hiddenMessage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A notice card (a message with one OK) hugs its content, as the question does, and keeps OK inside the card above the hint bar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GameNoticeCardHeightTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun check(
        what: String,
        title: String,
        text: TextSize,
        paper: Boolean,
        mustHug: Boolean,
    ) {
        val settings = (if (paper) PlumThemeSettings.Paper else PlumThemeSettings.Graphite).copy(textSize = text)
        val panel = OpenPanel(GameRef("snes-6", title), PanelScreen.Message(hiddenMessage(title)))
        compose.setContent { PlumTheme(settings) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) } }
        val card = bounds(GamePanelTags.CARD)
        val ok = bounds(GamePanelTags.row("ok"))
        val hints = bounds(HomeTags.HINT_BAR)
        val bar = bounds(HomeTags.TOP_BAR)
        val room = hints.top.value - bar.bottom.value
        assertTrue("$what: the card ends above the hint bar", card.bottom <= hints.top)
        assertTrue("$what: OK is inside the card", ok.top >= card.top && ok.bottom <= card.bottom)
        if (mustHug) assertTrue("$what: a short message is a short card (${(card.bottom - card.top).value} of $room)", (card.bottom - card.top).value < room * 0.75f)
    }

    private val shortTitle = "Super Metroid"
    private val longTitle = "Legend of the Mystical Ninja: The Very Long Title With Many Words That Wraps Over Several Lines Of The Card"

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun at832() {
        check("832 graphite", shortTitle, TextSize.Small, paper = false, mustHug = true)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun at768Paper() {
        check("768 paper", shortTitle, TextSize.Small, paper = true, mustHug = true)
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun at576() {
        check("576 graphite", shortTitle, TextSize.Small, paper = false, mustHug = true)
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun at576LargestLongTitle() {
        check("576 largest long", longTitle, TextSize.Largest, paper = true, mustHug = false)
    }
}
