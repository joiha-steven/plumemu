package com.plumemu.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import com.plumemu.coreinput.Action
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.FixBoxArtState
import com.plumemu.game.FixBoxArtTags
import com.plumemu.game.FixNameTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Fix box art… › Name (Plumemu's keyboard): a name longer than the field shows its START while the
 * person is not typing ("Captain Toad: …", not "…Treasure Tracker"), and slides to the end, where
 * the caret is, once A brings the keyboard up. What the line shows is read by a tap on its first
 * pixel: the caret lands on the first visible character (a tap's offset adds the line's shift).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class FixBoxArtNameStartTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val longName = "Captain Toad: Treasure Tracker (World) (Rev 1) (Special Collector's Edition Remaster)"

    private fun show(state: FixBoxArtState) {
        compose.setContent { PlumTheme { BoxArtPanelFixtures.Screen(BoxArtPanelFixtures.panel(state)) } }
        compose.waitForIdle()
    }

    /** Taps the line's first pixel and returns the caret that gave. */
    private fun caretAfterTapAtLineStart(state: FixBoxArtState): Int {
        compose.onNodeWithTag(FixNameTags.typedText(FixBoxArtTags.FIELD), useUnmergedTree = true)
            .performTouchInput { click(Offset(1f, center.y)) }
        compose.waitForIdle()
        return compose.runOnIdle { state.fieldKeys.caret }
    }

    @Test
    fun notTypingTheFirstVisibleCharacterIsTheNamesFirst() {
        val state = FixBoxArtState(longName, "Nintendo Switch")
        show(state)
        assertEquals("the caret is at the end of the name", longName.length, compose.runOnIdle { state.fieldKeys.caret })
        val caret = caretAfterTapAtLineStart(state)
        assertTrue("line starts at the name's start, caret $caret", caret <= 1)
    }

    @Test
    fun typingTheLineFollowsTheCaretAtTheEnd() {
        val state = FixBoxArtState(longName, "Nintendo Switch")
        show(state)
        compose.runOnIdle { state.onAction(Action.Enter) }
        compose.waitForIdle()
        val caret = caretAfterTapAtLineStart(state)
        assertTrue("typing: the line is slid toward the end, caret $caret of ${longName.length}", caret > longName.length / 3)
    }
}
