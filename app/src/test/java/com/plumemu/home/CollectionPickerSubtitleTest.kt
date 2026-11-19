package com.plumemu.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.collections.PickerCollection
import com.plumemu.collections.PickerState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Add to collection… picker names its game under the header, on one line that ends in an ellipsis when the title is long. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class CollectionPickerSubtitleTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val state = PickerState(listOf(PickerCollection(7, "RPG")), holding = setOf(7L))

    private fun show(title: String, text: TextSize) {
        val panel = OpenPanel(GameRef("snes-6", title), PanelScreen.CollectionPicker(state), focus = 0)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = text)) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) } }
        compose.waitForIdle()
    }

    private fun lines(tag: String): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.first().lineCount
    }

    @Test
    fun theGameTitleIsShownUnderTheHeader() {
        show("Super Metroid", TextSize.Medium)
        val subtitle = compose.onNodeWithTag(GamePanelTags.SUBTITLE, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("Super Metroid", subtitle.config[SemanticsProperties.Text].joinToString(""))
        val title = compose.onNodeWithTag(GamePanelTags.TITLE, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("under the header", subtitle.boundsInRoot.top >= title.bottom)
    }

    @Test
    fun aLongTitleStaysOnOneLineAtTheLargestSize() {
        show("The Legend of Zelda: A Link to the Past and Four Swords - Special Collector's Edition", TextSize.Largest)
        assertEquals(1, lines(GamePanelTags.SUBTITLE))
    }
}
