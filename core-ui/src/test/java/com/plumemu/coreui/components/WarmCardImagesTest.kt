package com.plumemu.coreui.components

import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CardDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Collections

/** The first opening of the game menu builds nothing on the main thread: its card's shadows and its row's slab are built beforehand. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class WarmCardImagesTest {
    @get:Rule
    val rule = createComposeRule()

    private val onMain = Collections.synchronizedList(mutableListOf<String>())

    @Before
    fun watch() {
        ImageBuilding.clearCaches()
        ImageBuilding.inBackground = true
        ImageBuilding.watcher = { name -> if (Looper.getMainLooper().thread === Thread.currentThread()) onMain += name }
    }

    @After
    fun stop() {
        ImageBuilding.watcher = null
        ImageBuilding.inBackground = false
        ImageBuilding.clearCaches()
    }

    @Test
    fun aCardOpenedAfterTheWarmUpBuildsNothingOnTheMainThread() {
        rule.mainClock.autoAdvance = false
        val showCard = mutableStateOf(false)
        val width = CardDimens.menuWidth
        val height = 340.dp
        val rowHeight = 40.dp
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                PlumTheme(PlumThemeSettings.Graphite) {
                    WarmCardImages(width, 432.dp, rowHeight)
                    val colors = PlumTheme.colors
                    Box(
                        Modifier.size(width, height).drawBehind {
                            if (!showCard.value) return@drawBehind
                            drawRaisedCard(colors.card, 0f, 0f, size.width, size.height)
                            val row = androidx.compose.ui.geometry.Size(size.width - 2 * PlumDimens.panelInset.toPx(), rowHeight.roundToPx().toFloat())
                            val outline = FocusPillShape.createOutline(row, layoutDirection, this)
                            drawSlabOutside(outline, colors.slab, colors.slabGlow, under = colors.card.fill)
                            drawSlabFill(outline, colors.slab, onCard = true)
                        },
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(WAIT_MS)
        repeat(POLLS) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.mainClock.advanceTimeBy(FRAME_MS)
            if (BackgroundBuilds.waiting > 0) Thread.sleep(POLL_MS)
        }
        showCard.value = true
        rule.mainClock.advanceTimeBy(WAIT_MS)
        rule.waitForIdle()
        // Draws the page, with the card in it.
        rule.onRoot().captureToImage()
        assertEquals(emptyList<String>(), onMain.toList())
    }

    private companion object {
        const val DENSITY = 2.5f
        const val WAIT_MS = 1_000L
        const val POLLS = 100
        const val POLL_MS = 20L
        const val FRAME_MS = 16L
    }
}
