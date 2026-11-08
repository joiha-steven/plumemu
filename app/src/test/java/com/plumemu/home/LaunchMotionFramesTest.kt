package com.plumemu.home

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.LocalLaunchOrigin
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.launcher.LaunchAnimation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The launch moment ([ProvideLaunchOrigin]): the home moves into the focused item and dims, frame by frame, toward the
 * item's centre, never further than [PlumMotion.LAUNCH_DEPTH] and never to black ([PlumMotion.LAUNCH_DIM]); the window is
 * asked for; nothing recomposes; with Animations off nothing moves and the start asks for no animation.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class LaunchMotionFramesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val game = Intent().setComponent(ComponentName("com.example.emulator", "com.example.emulator.Play")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    private var compositions = 0

    /** The page colour's green, as the home stands on it (MainScreen's background). */
    private var pageColour = 0f

    /** A white page with a focused item (40 dp square at 600, 100 dp) noting itself as the launch origin. */
    private fun show(animationsOn: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(animationsOn = animationsOn)) {
                pageColour = PlumTheme.colors.bg.green
                Box(Modifier.fillMaxSize().background(PlumTheme.colors.bg).testTag(ROOT)) {
                    ProvideLaunchOrigin {
                        SideEffect { compositions++ }
                        val origin = LocalLaunchOrigin.current
                        val placed = arrayOfNulls<LayoutCoordinates>(1)
                        Box(Modifier.fillMaxSize().background(Color.White)) {
                            Box(
                                Modifier
                                    .offset(600.dp, 100.dp)
                                    .size(40.dp)
                                    .onPlaced { placed[0] = it }
                                    .drawBehind { origin?.note(placed[0], Rect(0f, 0f, size.width, size.height)) }
                                    .background(Color.Red),
                            )
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        shadowOf(app).clearNextStartedActivities()
    }

    private fun frame(): PixelMap = compose.onNodeWithTag(ROOT).captureToImage().toPixelMap()

    /** The page's brightness far from the item (its top-left corner area). */
    private fun PixelMap.page(): Float = this[40, 400].green

    /** The item's left edge on its middle row. */
    private fun PixelMap.itemLeft(): Int = (0 until width).first { this[it, 240].let { p -> p.red > 0.3f && p.green < 0.2f } }

    private fun play() = compose.runOnUiThread { assertNotNull("the home is the host", LaunchAnimation.host); LaunchAnimation.host!!(game) }

    @Test
    fun theHomeMovesIntoTheItemAndDimsButNeverToBlack() {
        show(animationsOn = true)
        val restLeft = frame().itemLeft()
        play()
        assertEquals("the window is asked for", game.component, shadowOf(app).nextStartedActivity?.component)
        val pages = mutableListOf<Float>()
        val lefts = mutableListOf<Int>()
        repeat(PlumMotion.SCREEN_MS / 16 + 2) {
            compose.mainClock.advanceTimeByFrame()
            val shot = frame()
            pages += shot.page()
            lefts += shot.itemLeft()
        }
        compose.mainClock.advanceTimeBy(PlumMotion.SCREEN_MS.toLong())
        val end = frame()
        assertTrue("only dims: $pages", pages.zipWithNext().all { (a, b) -> b <= a + 0.01f })
        // Dimmed toward the page colour: LAUNCH_DIM of the white page is left.
        val dimmed = PlumMotion.LAUNCH_DIM + (1f - PlumMotion.LAUNCH_DIM) * pageColour
        assertEquals("ends at the dim, not black", dimmed, end.page(), 0.03f)
        // Growing about the item's centre: its left edge moves left by at most half of 8 % of its 80 px.
        assertTrue("grows about the item: $restLeft $lefts", lefts.all { it in (restLeft - 4)..restLeft })
        assertEquals("a frame of motion recomposes nothing", 1, compositions)
    }

    @Test
    fun withAnimationsOffNothingMovesAndTheStartIsPlain() {
        show(animationsOn = false)
        val before = frame()
        play()
        assertEquals(game.component, shadowOf(app).nextStartedActivity?.component)
        compose.mainClock.advanceTimeBy(PlumMotion.SCREEN_MS + 50L)
        assertEquals(before.page(), frame().page(), 0.01f)
        assertEquals(before.itemLeft(), frame().itemLeft())
    }

    private companion object {
        const val ROOT = "root"
    }
}
