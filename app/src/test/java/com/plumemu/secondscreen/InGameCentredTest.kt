package com.plumemu.secondscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpOffset
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.BatteryReading
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The in-game card stays centred (it sat off to the right and never seemed to
 * move): at the Thor's bottom screen (538x468 dp at 369 dpi), over every minute of one full period
 * of [PixelShift], the block as drawn is within the drift box of where the still card is drawn,
 * and the still block's layout centre is the window's centre within a pixel. The drawn position is
 * read from the pixels: the semantics bounds (`boundsInRoot`) do not include the graphicsLayer
 * translation (measured: they read 0 px at every minute of the old 112x80 dp path).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class InGameCentredTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var shift by mutableStateOf(DpOffset.Zero)
    private var battery by mutableStateOf<BatteryReading?>(BatteryReading(percent = 82, isCharging = false))
    private var wifi by mutableStateOf(true)
    private var density = 1f

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w538dp-h468dp-land-369dpi")
        density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        compose.setContent { InGameCard(time = "21:47", battery = battery, wifi = wifi, shift = { shift }) }
    }

    @Test
    fun withBatteryAndWifiTheBlockStaysWithinTheDriftBoxOfTheCentre() = everyMinuteStaysNearTheCentre()

    @Test
    fun withTheClockOnlyTheBlockStaysWithinTheDriftBoxOfTheCentre() {
        battery = null
        wifi = false
        everyMinuteStaysNearTheCentre()
    }

    private fun everyMinuteStaysNearTheCentre() {
        compose.waitForIdle()
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val block = compose.onNodeWithTag(InGameTags.BLOCK).fetchSemanticsNode().boundsInRoot
        val layoutX = block.center.x - window.center.x
        val layoutY = block.center.y - window.center.y
        assertTrue("no shift: layout centre $layoutX, $layoutY px off the window's centre", abs(layoutX) <= 1f && abs(layoutY) <= 1f)
        val still = drawnCentre()
        var worstX = 0f
        var worstY = 0f
        for (minute in 0L until PixelShift.PERIOD_MINUTES) {
            shift = PixelShift.offsetAt(minute)
            compose.waitForIdle()
            val drawn = drawnCentre()
            worstX = maxOf(worstX, abs(drawn.first - still.first))
            worstY = maxOf(worstY, abs(drawn.second - still.second))
        }
        println("In-game card drift over ${PixelShift.PERIOD_MINUTES} minutes: at most $worstX px sideways, $worstY px up or down (density $density)")
        assertTrue("sideways $worstX px > ${MAX_X_DP * density + 1f}", worstX <= MAX_X_DP * density + 1f)
        assertTrue("vertical $worstY px > ${MAX_Y_DP * density + 1f}", worstY <= MAX_Y_DP * density + 1f)
    }

    /** The centre of the lit pixels (the drawn block, translation included), in px. */
    private fun drawnCentre(): Pair<Float, Float> {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        var left = Int.MAX_VALUE
        var right = -1
        var top = Int.MAX_VALUE
        var bottom = -1
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val c = pixels[x, y]
                if (c.red > 0f || c.green > 0f || c.blue > 0f) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        assertTrue("nothing drawn", right >= 0)
        return Pair((left + right) / 2f, (top + bottom) / 2f)
    }

    private companion object {
        const val MAX_X_DP = 4f
        const val MAX_Y_DP = 12f
    }
}
