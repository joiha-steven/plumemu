package com.plumemu.settings.screen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import kotlin.math.abs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Library › Systems: a system picked up with A (carried) must look different from one that is only focused (both used to be the same pill). Read from the drawn pixels below the ring: the focused row shows the pill,
 * the carried one the solid accent.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsSystemsCarriedLookTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun Color.near(other: Color) = abs(red - other.red) < 0.02f && abs(green - other.green) < 0.02f && abs(blue - other.blue) < 0.02f

    private fun check(theme: PlumThemeSettings) {
        val colors = PlumComposeColors.from(PlumColors.derive(theme.backgroundColor, theme.textMode, theme.accentColor))
        var presses: ShotPresses? = null
        compose.setContent {
            PlumTheme(theme) {
                SystemsShot(SettingsOption.Systems, enter = true, order = com.plumemu.coredata.settings.SystemOrderSetting.Manual) {
                    presses = this
                    move(Direction.Down)
                }
            }
        }
        compose.waitForIdle()
        fun fillPixel(): Color {
            val image = compose.onNodeWithTag(SettingsTags.panelItem(1)).captureToImage()
            return image.toPixelMap()[image.width / 2, FILL_Y]
        }
        val focused = fillPixel()
        assertTrue("focused: the pill", focused.near(colors.pill))
        compose.runOnUiThread { presses!!.press(Action.Enter) }
        compose.waitForIdle()
        val carried = fillPixel()
        assertTrue("carried: the solid accent", carried.near(colors.accent))
        assertFalse("carried differs from focused", carried.near(focused))
    }

    @Test
    fun carriedRowIsToldFromFocusedOnGraphite() = check(PlumThemeSettings.Graphite)

    @Test
    fun carriedRowIsToldFromFocusedOnPaper() = check(PlumThemeSettings.Paper)

    private companion object {
        /** Below the 2 dp ring (4 px at xhdpi), inside the fill. */
        const val FILL_Y = 10
    }
}
