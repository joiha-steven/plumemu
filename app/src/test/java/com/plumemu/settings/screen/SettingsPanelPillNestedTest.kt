package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.focusPill
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.LocalGlideClock
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
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
import kotlin.math.abs

/**
 * A [PanelLazyList] row that sits inside a wrapper (padding above it in a Box) keeps its pill on it while the list scrolls: the
 * wrapper moves with the scroll without the row being placed again, so the pill reads the row's place live, every frame.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class SettingsPanelPillNestedBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val focused = mutableIntStateOf(0)

    private fun inner(i: Int) = "nested_row_$i"

    private fun top(tag: String): Float = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun thePillStaysOnANestedRowWhileTheListScrolls() {
        compose.setContent {
            CompositionLocalProvider(LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L }) {
                PlumTheme(PlumThemeSettings.Default) {
                    Box(Modifier.size(400.dp, 300.dp)) {
                        PanelLazyList(focused.intValue, Modifier.size(400.dp, 300.dp)) {
                            items(40, key = { it }) { i ->
                                Box(Modifier.padding(top = 10.dp)) { Box(Modifier.fillMaxWidth().height(40.dp).testTag(inner(i)).focusPill(i == focused.intValue)) }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val list = compose.onNodeWithTag(SettingsTags.PANEL_LIST, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val ring = compose.onRoot().captureToImage().toPixelMap()[(list.left + list.width * 0.55f).toInt(), top(inner(0)).toInt() + 1]
        fun pillTop(): Int {
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val from = (list.left + list.width * 0.45f).toInt()
            val to = (list.left + list.width * 0.65f).toInt()
            for (y in list.top.toInt() until list.bottom.toInt()) {
                if ((from until to).count { x -> pixels[x, y].let { c -> abs(c.red - ring.red) + abs(c.green - ring.green) + abs(c.blue - ring.blue) < 0.12f } } * 2 >= to - from) return y
            }
            return -1
        }
        assertEquals("at rest the pill is on the first row", top(inner(0)).toInt(), pillTop())
        val stride = top(inner(1)) - top(inner(0))
        compose.mainClock.autoAdvance = false
        val worst = mutableListOf<String>()
        for (frame in 0 until 90) {
            if (frame % 4 == 0 && focused.intValue < 20) compose.runOnIdle { focused.intValue++ }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val pill = pillTop()
            val row = top(inner(focused.intValue))
            if (pill < 0) worst += "frame $frame: no pill"
            // The pill glides and, in a hold, trails its row at the repeats' speed (as home's does); it never loses it.
            else if (abs(pill - row) > 3.5f * stride) worst += "frame $frame: pill $pill row $row"
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("never far from its row: $worst", emptyList<String>(), worst)
        assertEquals("settled on the focused row", top(inner(focused.intValue)).toInt(), pillTop())
    }
}

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsPanelPillNestedTest : SettingsPanelPillNestedBase()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsPanelPillNested576Test : SettingsPanelPillNestedBase()
