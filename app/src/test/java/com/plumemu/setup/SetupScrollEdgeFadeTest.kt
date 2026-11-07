package com.plumemu.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
 * A Setup scroll stopped mid-way (Ready at the largest text): what it shows at its cut bottom edge fades out, it is not cut flat
 * through a line or a card. The last row above the cut is nearly the page; a stretch above it is not.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
class SetupScrollEdgeFadeTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var theme by mutableStateOf(PlumThemeSettings.Paper.copy(textSize = TextSize.Largest))
    private var density = Density(1f)
    private var page = Color.Unspecified

    private fun px(dp: Dp) = with(density) { dp.toPx() }

    private fun away(a: Color) = maxOf(abs(a.red - page.red), abs(a.green - page.green), abs(a.blue - page.blue))

    @Test
    fun aScrolledReadyFadesAtItsBottomEdge() {
        val state = SetupFakes.ready.copy(asksAboutStatistics = true, focus = SetupControl.UsageStatistics)
        compose.setContent {
            density = LocalDensity.current
            PlumTheme(theme) {
                page = PlumTheme.colors.bg
                Box(Modifier.fillMaxSize()) { SetupAt(state) }
            }
        }
        val problems = mutableListOf<String>()
        for ((name, look) in listOf("Paper" to PlumThemeSettings.Paper, "Graphite" to PlumThemeSettings.Graphite)) {
            theme = look.copy(textSize = TextSize.Largest)
            compose.waitForIdle()
            // Short drags: at some stops the cut bottom runs through a line or a card.
            repeat(STOPS) { stop ->
                compose.onNodeWithTag(SetupTags.SCROLL, useUnmergedTree = true).performTouchInput { swipeUp(startY = centerY, endY = centerY - DRAG_PX) }
                compose.waitForIdle()
                val scroll = compose.onNodeWithTag(SetupTags.SCROLL, useUnmergedTree = true).getUnclippedBoundsInRoot()
                val pixels = compose.onRoot().captureToImage().toPixelMap()
                val cut = px(scroll.bottom - SlabDimens.listEndRoom).toInt()
                val fade = px(PlumDimens.listFadeBand).toInt()
                val left = px(scroll.left + SlabDimens.listEndRoom).toInt()
                val right = px(scroll.right - SlabDimens.listEndRoom).toInt()
                fun rowAway(y: Int) = (left until right).sumOf { away(pixels[it, y]).toDouble() } / (right - left)
                val above = (cut - 3 * fade until cut - fade).maxOf { rowAway(it) }
                // The worst of the last rows: the fade is nearly clear at the edge, a flat cut is not.
                val last = (cut - 3 until cut).maxOf { rowAway(it) }
                if (above >= MIN_CONTENT && last > above * MAX_SHARE) problems += "$name stop $stop: the last rows above the cut are ${last * 255}/255 from the page, ${above * 255}/255 above: cut flat"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        const val MIN_CONTENT = 2f / 255f
        const val MAX_SHARE = 0.35
        const val STOPS = 8
        const val DRAG_PX = 70f
    }
}
