package com.plumemu.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A step resting on a line leaves nothing of the line above it: the rows between the scroll's clip and the window's top (the focus ring's reach)
 * stayed drawn, and a Nova showed the lowest parts of a scrolled-away line's letters there, a row of faint specks under the status line.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w640dp-h480dp-land-xhdpi")
class SetupRestsWholeLinesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var height by mutableStateOf(480)
    private var density = Density(1f)
    private var page = Color.Unspecified

    private fun scrollValue() =
        compose.onNodeWithTag(SetupTags.SCROLL, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    fun aRestingStepLeavesNoRemainsOfTheLineAboveIt() {
        val state = SetupFakes.readyWithBoxArt.copy(asksAboutStatistics = true)
        compose.setContent {
            density = LocalDensity.current
            PlumTheme(PlumThemeSettings.Paper.copy(textSize = TextSize.Larger)) {
                page = PlumTheme.colors.bg
                key(height) { Box(Modifier.height(height.dp)) { SetupAt(state) } }
            }
        }
        val problems = mutableListOf<String>()
        var rests = 0
        for (tall in 300..476 step 2) {
            height = tall
            compose.waitForIdle()
            if (scrollValue() <= 0f) continue
            rests++
            val top = compose.onNodeWithTag(SetupTags.SCROLL, useUnmergedTree = true).getUnclippedBoundsInRoot().top
            // Past the title block the rows slide under a fade, as every list; only a stop on the block's lines is a rest.
            if (compose.onNodeWithTag(SetupTags.DESCRIPTION, useUnmergedTree = true).getUnclippedBoundsInRoot().bottom < top + SlabDimens.listEndRoom - 1.dp) continue
            val clip = with(density) { (top + SlabDimens.listEndRoom).toPx() }.toInt()
            val window = with(density) { (top + SlabDimens.listEndRoom + PlumDimens.focusRingOutset).toPx() }.toInt()
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val worst =
                (clip until window).maxOf { y ->
                    (0 until pixels.width).maxOf { x -> pixels[x, y].let { maxOf(abs(it.red - page.red), abs(it.green - page.green), abs(it.blue - page.blue)) } }
                }
            if (worst > NOTHING) problems += "At $tall dp high, scrolled ${scrollValue()}: a pixel above the window is ${worst * 255}/255 from the page"
        }
        assertTrue("The sweep never rested on a line", rests > 0)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        const val NOTHING = 2f / 255f
    }
}
