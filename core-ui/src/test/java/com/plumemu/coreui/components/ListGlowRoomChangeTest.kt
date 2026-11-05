package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.rememberListGlide
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A list with motion on whose glow room is taken away after its first layout (a list that was measured to fit, then did not) is clipped
 * at the slab's overhang from then on, not still at the wider glow room: what shows past the list's end is only what the card's edge fade covers.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class ListGlowRoomChangeTest {
    @get:Rule
    val compose = createComposeRule()

    private val margin = 40.dp

    /** Rows of the list's filler drawn above the list's top, in px, once its glow room is [roomAfter] (starting from [roomBefore]). */
    private fun drawnAbove(roomBefore: Boolean, roomAfter: Boolean): Int {
        var room by mutableStateOf(roomBefore)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                val glide = rememberListGlide(stride = 40.dp, glowRoom = room)
                Box(Modifier.testTag("box").background(Color.Black).padding(margin)) {
                    Box(Modifier.fillMaxWidth().height(100.dp).then(glide.modifier)) {
                        Box(Modifier.fillMaxSize().background(Color.Red))
                    }
                }
            }
        }
        compose.waitForIdle()
        room = roomAfter
        compose.waitForIdle()
        val image = compose.onNodeWithTag("box").captureToImage().toPixelMap()
        val top = (margin.value * compose.density.density).toInt()
        return (0 until top).count { image[image.width / 2, it].red > 0.5f }
    }

    private fun px(dp: androidx.compose.ui.unit.Dp) = dp.value * compose.density.density

    @Test
    fun aListWithGlowRoomClipsAtTheEndRoom() {
        assertEquals(px(SlabDimens.listEndRoom), drawnAbove(roomBefore = true, roomAfter = true).toFloat(), 1.5f)
    }

    @Test
    fun aListWithoutGlowRoomClipsAtTheOverhang() {
        assertEquals(px(SlabDimens.maxOverhang), drawnAbove(roomBefore = false, roomAfter = false).toFloat(), 1.5f)
    }

    @Test
    fun aListThatLosesItsGlowRoomIsClippedAsOneThatNeverHadIt() {
        assertEquals(px(SlabDimens.maxOverhang), drawnAbove(roomBefore = true, roomAfter = false).toFloat(), 1.5f)
    }

    @Test
    fun aListThatGainsGlowRoomIsClippedAsOneThatAlwaysHadIt() {
        assertEquals(px(SlabDimens.listEndRoom), drawnAbove(roomBefore = false, roomAfter = true).toFloat(), 1.5f)
    }
}
