package com.plumemu.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.down
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column as StripColumn
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.ButtonLetters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Ready step appears with Start playing below the scroll's edge and scrolls it into view
 * for about a quarter of a second. A finger that lands in that time stops the scroll instead of tapping,
 * so the first tap on Start playing did nothing (the end-to-end test tapped 20 ms after the animation).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SetupReadyTapTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val actions = mutableListOf<Action>()

    @Test
    fun aTapAfterTheScrollSettledTapsStartPlaying() {
        compose.mainClock.autoAdvance = false
        show()
        compose.mainClock.advanceTimeBy(SETTLED_MS)
        compose.onNodeWithTag(SetupTags.control(SetupControl.StartPlaying)).performTouchInput {
            down(center)
            up()
        }
        compose.mainClock.advanceTimeBy(FRAMES_IN_MS)
        assertEquals(1, actions.size)
    }

    @Test
    fun aTapDuringTheReadyStepsFirstScrollStillTapsStartPlaying() {
        compose.mainClock.autoAdvance = false
        show()
        compose.mainClock.advanceTimeBy(FRAMES_IN_MS)
        compose.onNodeWithTag(SetupTags.control(SetupControl.StartPlaying)).performTouchInput {
            down(center)
            up()
        }
        compose.mainClock.advanceTimeBy(FRAMES_IN_MS)
        val startIndex = SetupFakes.readyStillNaming.controls.indexOf(SetupControl.StartPlaying)
        assertEquals("one tap on Start playing", listOf<Action>(Action.TapItem(StripColumn.Games, startIndex)), actions)
        assertTrue(startIndex >= 0)
    }

    private fun show() =
        compose.setContent {
            // Short enough that Start playing starts below the edge, as it does on the emulator's 1920 x 1080.
            Box(Modifier.height(READY_SCREEN_HEIGHT.dp)) {
                SetupLayout(SetupFakes.readyStillNaming, ButtonLetters.Default, onAction = { actions += it }, clock = { "21:40" }, battery = { "82%" })
            }
        }

    private companion object {
        const val SETTLED_MS = 2_000L
        const val READY_SCREEN_HEIGHT = 300
        const val FRAMES_IN_MS = 50L
    }
}
