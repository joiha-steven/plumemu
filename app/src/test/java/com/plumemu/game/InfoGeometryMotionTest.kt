package com.plumemu.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Info's list geometry (the facts window and the Back row) is the same with motion on and off, at 576 x 432, 640 x 480 and 768 x 432. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class InfoGeometryMotionTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val facts = sampleFacts(title = "Super Metroid").copy(systemName = "Super Nintendo", regions = "USA,EUR", fileName = "Super Metroid.sfc")
    private val panel = OpenPanel(GameRef("7", facts.title), PanelScreen.Info(facts))
    private var isMotionOn by mutableStateOf(false)

    private fun geometry(): Pair<Int, Int> {
        val window = compose.onNodeWithTag(GamePanelTags.FACTS_SCROLL, useUnmergedTree = true).fetchSemanticsNode()
        val back = compose.onNodeWithTag(GamePanelTags.row(INFO_BACK_KEY), useUnmergedTree = true).fetchSemanticsNode()
        return window.size.height to back.positionInRoot.y.toInt()
    }

    private fun check(size: TextSize) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = PlumFont.Serif, animationsOn = isMotionOn)) {
                HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel)
            }
        }
        compose.waitForIdle()
        val off = geometry()
        isMotionOn = true
        compose.waitForIdle()
        assertEquals("(facts window height, Back row top) at $size, motion off then on", off, geometry())
    }

    @Test fun at576x432Largest() = check(TextSize.Largest)

    @Test fun at576x432Medium() = check(TextSize.Small)

    @Test @Config(qualifiers = "w640dp-h480dp-land-xhdpi") fun at640x480Largest() = check(TextSize.Largest)

    @Test @Config(qualifiers = "w640dp-h480dp-land-xhdpi") fun at640x480Medium() = check(TextSize.Small)

    @Test @Config(qualifiers = "w768dp-h432dp-land-xhdpi") fun at768x432Largest() = check(TextSize.Largest)

    @Test @Config(qualifiers = "w768dp-h432dp-land-xhdpi") fun at768x432Medium() = check(TextSize.Small)
}
