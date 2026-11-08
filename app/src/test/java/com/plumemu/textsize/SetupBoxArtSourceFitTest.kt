package com.plumemu.textsize

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.HomeTags
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.setup.SetupAt
import com.plumemu.setup.SetupControl
import com.plumemu.setup.SetupFakes
import com.plumemu.setup.SetupTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Setup's box art source page (page 2) at the six text sizes in Jost, as `TextSizeScreensTest` checks the other setup
 * screens (kept apart: that file is at the length limit): no text cut, ellipsized or on top of another, the focused
 * card whole. At Largest the 837x471 and 576x432 renderings are kept as `textsize_largest_setup_box_art_source_*`.
 */
abstract class SetupBoxArtSourceFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun theSourcesFitAtEverySize() {
        var size by mutableStateOf(TextSize.Largest)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { SetupAt(SetupFakes.boxArtSources) } }
        val failures = mutableListOf<String>()
        for (each in listOf(TextSize.Largest) + TextSize.entries.filter { it != TextSize.Largest }) {
            size = each
            compose.waitForIdle()
            if ((widthDp == 837 || widthDp == 576) && each == TextSize.Largest) {
                compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/textsize_largest_setup_box_art_source_${widthDp}x${heightDp}_graphite.png")
            }
            val label = "setup_box_art_source at ${each.name} (${each.factor}) ${PlumFont.Jost.name} on ${widthDp}x$heightDp"
            val screenRoot = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val allowance = FitAllowance(setOf(SetupTags.SCROLL), isLargest = each == TextSize.Largest, barTags = setOf(HomeTags.TOP_BAR, HomeTags.HINT_BAR))
            textProblems(screenRoot, allowance).mapTo(failures) { "$label: $it" }
            val card = findTagged(screenRoot, SetupTags.control(SetupControl.BoxArtLibretro))
            if (card == null) failures += "$label: the focused card is not drawn" else notFullyShown(card, screenRoot, 0f)?.let { failures += "$label: the focused card $it" }
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SetupBoxArtSourceFit768Test : SetupBoxArtSourceFitBase(768, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class SetupBoxArtSourceFit837Test : SetupBoxArtSourceFitBase(837, 471)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class SetupBoxArtSourceFit667Test : SetupBoxArtSourceFitBase(667, 375)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SetupBoxArtSourceFit576Test : SetupBoxArtSourceFitBase(576, 432)
