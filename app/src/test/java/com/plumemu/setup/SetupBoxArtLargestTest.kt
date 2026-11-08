package com.plumemu.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The box art step's two cards, on both pages, are whole on the 576 x 432 handheld at the Largest text size without a
 * scroll: the old third card was cut there.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SetupBoxArtLargestTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun assertWhole(state: SetupUiState) {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) { Box(Modifier.fillMaxSize()) { SetupAt(state) } } }
        compose.waitForIdle()
        val hints = compose.onNodeWithTag(SetupTags.HINTS, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("two cards", 2, state.controls.size)
        for (control in state.controls) {
            val node = compose.onNodeWithTag(SetupTags.control(control), useUnmergedTree = true)
            val whole = node.getUnclippedBoundsInRoot()
            val shown = node.getBoundsInRoot()
            assertEquals("$control: its whole height is on screen", whole.bottom.value - whole.top.value, shown.bottom.value - shown.top.value, TOLERANCE)
            assertTrue(
                "$control ends at ${whole.bottom}, the hints start at ${hints.top}",
                hints.top.value - whole.bottom.value >= PlumDimens.focusRingOutset.value - TOLERANCE,
            )
        }
    }

    @Test
    fun theQuestionsTwoCardsAreWholeAtLargest() = assertWhole(SetupFakes.boxArtQuestion)

    @Test
    fun theSourcesTwoCardsAreWholeAtLargest() = assertWhole(SetupFakes.boxArtSources)

    private companion object {
        const val TOLERANCE = 0.6f
    }
}
