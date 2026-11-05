package com.plumemu.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.EdgeFades
import com.plumemu.coreui.components.listEdgeFadeMask
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The one fade rule for lists: the share of a fade next to its edge is fully clear, so a row cut there shows no
 * sliver of a glyph top or bottom, only the ramp. Content here is solid white on black.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class EdgeFadeClearEdgeTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val both =
        object : EdgeFades {
            override val canScroll = true

            override fun top(fadePx: Float, height: Float) = fadePx

            override fun bottom(fadePx: Float, height: Float) = fadePx
        }

    private fun red(
        bitmap: android.graphics.Bitmap,
        y: Int,
    ) = android.graphics.Color.red(bitmap.getPixel(100, y))

    @Test
    fun theLastShareOfEachFadeIsClearAndTheRampStartsOpaque() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.width(100.dp).height(200.dp).listEdgeFadeMask(both, true).background(Color.White))
            }
        }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val fade = (PlumDimens.listEdgeFade.value * 2).toInt()
        val height = 400
        assertEquals("top edge row", 0, red(bitmap, 0))
        assertEquals("top: inside the clear share", 0, red(bitmap, fade * 3 / 10 - 2))
        assertEquals("bottom edge row", 0, red(bitmap, height - 1))
        assertEquals("bottom: inside the clear share", 0, red(bitmap, height - fade * 3 / 10 + 2))
        assertTrue("middle of the content is whole", red(bitmap, height / 2) == 255)
        assertTrue("the ramp is part way in the middle of the band", red(bitmap, height - fade / 2) in 1..254)
    }
}
