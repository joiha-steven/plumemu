package com.plumemu.coreui.components.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The focused action key must look focused to a gamepad user: its accent fill swallows the accent
 * ring, so an `onAccent` ring is drawn inside the fill. Read from the drawn pixels, on Graphite and
 * Paper.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class KeyboardFocusLookTest {
    @get:Rule
    val compose = createComposeRule()

    private val spec = KeyboardSpec(actionLabel = "Search", hasShift = false)
    private val state = KeyboardState(spec.startModel().let { it.focusOn(it.idOf("ACTION")) })

    private fun actionImage(): ImageBitmap = compose.onNodeWithTag(keyboardKeyTag(state.model.idOf("ACTION"))).captureToImage()

    private fun Color.near(other: Color): Boolean =
        abs(red - other.red) < 0.02f && abs(green - other.green) < 0.02f && abs(blue - other.blue) < 0.02f

    private fun check(settings: PlumThemeSettings) {
        val colors = PlumComposeColors.from(PlumColors.derive(settings.backgroundColor, settings.textMode, settings.accentColor))
        compose.setContent { PlumTheme(settings) { KeyboardOnScreen(state, spec) } }
        compose.waitForIdle()
        val px = compose.density.density
        // On the inner ring's line: half a gap, the 3 dp inset and half the ring's width below the
        // cell's top, 20 dp in from its left (past the rounded corner, left of the word).
        val y = ((PlumDimens.keyboardKeyGap.value / 2 + PlumDimens.keyboardActionRingInset.value + PlumDimens.keyboardFocusRing.value / 2) * px).toInt()
        val x = (20f * px).toInt()
        val focused = actionImage().toPixelMap()
        assertTrue("focused: ${focused[x, y]} is not onAccent ${colors.onAccent}", focused[x, y].near(colors.onAccent))

        compose.runOnIdle { state.move(KeyboardDirection.Left) }
        compose.waitForIdle()
        val plain = actionImage().toPixelMap()
        assertTrue("unfocused: ${plain[x, y]} is not the accent fill ${colors.accent}", plain[x, y].near(colors.accent))
        // And the two looks differ along the whole ring, not in one pixel.
        var differing = 0
        for (i in 0 until plain.width) for (j in 0 until plain.height) if (!plain[i, j].near(focused[i, j])) differing++
        assertTrue("only $differing pixels differ", differing > 4 * plain.width)
        assertEquals(plain.width, focused.width)
    }

    @Test
    fun focusedActionKeyShowsAnInnerRingOnGraphite() = check(PlumThemeSettings.Graphite)

    @Test
    fun focusedActionKeyShowsAnInnerRingOnPaper() = check(PlumThemeSettings.Paper)
}
