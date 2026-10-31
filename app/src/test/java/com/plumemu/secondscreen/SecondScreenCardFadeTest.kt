package com.plumemu.secondscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumMotion
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * No fade on the second screen:
 * the first content of a fresh card and every later one are at full brightness on their first frame.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenCardFadeTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var content by mutableStateOf<SecondScreenContent>(GAME_NEVER_PLAYED)

    private fun brightest(): Float {
        val pixels = compose.onNodeWithTag(SecondScreenTags.CARD).captureToImage().toPixelMap()
        var best = 0f
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) best = maxOf(best, pixels[x, y].red)
        return best
    }

    @Test
    fun everyContentIsAtFullBrightnessOnItsFirstFrame() {
        RuntimeEnvironment.setQualifiers("w${SecondSize.Roomy.widthDp}dp-h${SecondSize.Roomy.heightDp}dp-land-xhdpi")
        compose.mainClock.autoAdvance = false
        compose.setContent { PlumTheme(PlumThemeSettings.Default) { SecondScreenCard(content) } }
        compose.mainClock.advanceTimeBy(1L)
        val atOnce = brightest()
        assertTrue("drawn at once: $atOnce", atOnce > 0.5f)
        compose.mainClock.advanceTimeBy(PlumMotion.FADE_IN_MS * 2L)
        assertEquals("nothing changes afterwards", atOnce, brightest(), 0.02f)
        compose.runOnIdle { content = GAME_WITH_STAR }
        compose.mainClock.advanceTimeByFrame()
        assertEquals("a new content on its first frame: no fade", atOnce, brightest(), 0.02f)
    }
}
