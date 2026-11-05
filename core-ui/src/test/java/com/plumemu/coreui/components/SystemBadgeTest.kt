package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The system tag is 34 dp tall (times the text size's scale) and as wide as its text plus 12 dp each side, so a long name is never cut. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SystemBadgeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun check(abbr: String, settings: PlumThemeSettings) {
        var textWidth = 0f
        var scale = 1f
        compose.setContent {
            PlumTheme(settings) {
                val density = LocalDensity.current
                val measurer = rememberTextMeasurer()
                textWidth = with(density) { measurer.measure(abbr.uppercase(), systemBadgeTextStyle(), maxLines = 1, softWrap = false).size.width.toDp().value }
                scale = PlumTheme.layout.textScale
                Box { SystemBadge(abbr) }
            }
        }
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag(SYSTEM_BADGE_TAG).getUnclippedBoundsInRoot()
        assertEquals("$abbr height", PlumDimens.systemBadgeHeight.value * scale, bounds.height.value, 0.6f)
        assertTrue("$abbr is as wide as its text plus the padding (${bounds.width.value} < ${textWidth + 24f})", bounds.width.value >= textWidth + 24f - 0.6f)
    }

    @Test fun shortNameMedium() = check("SNES", PlumThemeSettings.Graphite)

    @Test fun arcadeLengthNeverCut() = check("ARCADE", PlumThemeSettings.Paper)

    @Test fun arcadeLengthAtLargestText() = check("ARCADE", PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest))
}
