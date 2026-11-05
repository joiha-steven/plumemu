package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A switch inside a row: the focused row's slab says "focused", so the switch keeps its resting collar (no lit groove, no ring).
 * Its groove lights only when the switch itself is the focus target (`PlumSwitch(focused = true)`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class FocusSlabSwitchTest {
    @get:Rule
    val compose = createComposeRule()

    /** Three rows in one composition: resting, focused, and resting with a switch that is itself the target; the switch pixels of each. */
    private fun switches(settings: PlumThemeSettings): Triple<List<Color>, List<Color>, List<Color>> {
        compose.setContent {
            PlumTheme(settings) {
                // Apart, so the focused row's shadow and glow do not reach the others' switches.
                Column(Modifier.width(360.dp).background(PlumTheme.colors.bg), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    for ((i, row) in listOf(false to false, true to false, false to true).withIndex()) {
                        // A resting row gets the slab's face (gradient and inset shadows) behind it too, so the lip's soft edge blends with the same colour in all three and only the switch itself can differ.
                        // Each row sits in its own clip (the Paper slab's blue glow reaches over 100 dp, far past the gap), roomy enough for the switch's own shadow.
                        Box(Modifier.clipToBounds().padding(vertical = 16.dp)) {
                            Row(Modifier.fillMaxWidth().height(PlumTheme.layout.rowHeight).slabFaceBehind().focusPill(row.first).padding(horizontal = PlumDimens.gameRowPadding), verticalAlignment = Alignment.CenterVertically) {
                                PlumSwitch(on = true, focused = row.second, modifier = Modifier.testTag("sw$i"))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        fun pixels(tag: String): List<Color> {
            val map = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            return buildList { for (y in 0 until map.height) for (x in 0 until map.width) add(map[x, y]) }
        }
        return Triple(pixels("sw0"), pixels("sw1"), pixels("sw2"))
    }

    /** Pixels that differ by more than [BIG] in a channel: a lit groove or ring, any real change. */
    private fun differing(
        a: List<Color>,
        b: List<Color>,
    ): Int {
        assertEquals(a.size, b.size)
        return a.indices.count { i -> abs(a[i].red - b[i].red) > BIG || abs(a[i].green - b[i].green) > BIG || abs(a[i].blue - b[i].blue) > BIG }
    }

    private fun check(settings: PlumThemeSettings) {
        val (rest, inFocusedRow, target) = switches(settings)
        assertEquals("a switch in a focused row looks as it does at rest", 0, differing(rest, inFocusedRow))
        assertTrue("a switch that is itself the target lights its groove", differing(rest, target) > 0)
    }

    @Test fun graphiteSwitchInAFocusedRowKeepsItsRestingCollar() = check(PlumThemeSettings.Graphite)

    @Test fun paperSwitchInAFocusedRowKeepsItsRestingCollar() = check(PlumThemeSettings.Paper)

    private companion object {
        const val BIG = 0.02f
    }
}

/** The focused slab's gradient and inset shadows behind a row, without its edge, bar or shadows. */
@androidx.compose.runtime.Composable
private fun Modifier.slabFaceBehind(): Modifier {
    val colors = PlumTheme.colors
    return drawBehind { drawSlabFill(FocusPillShape.createOutline(size, layoutDirection, this), colors.slab, onCard = false) }
}
