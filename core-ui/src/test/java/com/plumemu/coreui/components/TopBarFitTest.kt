package com.plumemu.coreui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.TypeRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The top bar's name on the smallest reference screen with the clock, Wi-Fi and a charging battery: drawn in 20 when its whole line
 * fits, else in 16, and cut with an ellipsis only when 16 does not fit either.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class TopBarFitTest {
    @get:Rule
    val compose = createComposeRule()

    private var settings by mutableStateOf(PlumThemeSettings.Graphite)
    private var place by mutableStateOf("")
    private var emphasis by mutableStateOf("")
    private var last = ""

    private fun show() =
        compose.setContent {
            PlumTheme(settings) {
                TopBar(place, emphasis, clock = { "21:40" }, battery = { "82%" }, wifi = { true }, charging = { true })
            }
        }

    /** The name's laid-out line: its size in sp and whether it was cut. */
    private fun name(
        font: PlumFont,
        size: TextSize,
        text: String,
        rest: String,
    ): Pair<Float, Boolean> {
        compose.runOnIdle {
            settings = settings.copy(font = font, textSize = size)
            place = if (rest.isEmpty()) text else "$text · $rest"
            emphasis = text
        }
        compose.waitForIdle()
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        val layout = results.single()
        last = "size ${layout.size} max ${layout.layoutInput.constraints} lines ${layout.lineCount} ell ${layout.isLineEllipsized(0)} ow ${layout.didOverflowWidth} text ${layout.layoutInput.text}"
        return layout.layoutInput.style.fontSize.value to (layout.isLineEllipsized(0) || layout.getLineRight(0) > layout.layoutInput.constraints.maxWidth)
    }

    private fun full(font: PlumFont, size: TextSize) = TypeRole.PlaceName.scaledSizeSp(font, size)

    private fun stepped(font: PlumFont, size: TextSize) = full(font, size) * PLACE_NAME_STEP_DOWN / TypeRole.PlaceName.sizeSp

    @Test
    fun aLongSystemNameStepsDownTo16BeforeItIsCutInEveryFont() {
        show()
        val text = "Nintendo Entertainment System"
        for (font in PlumFont.entries) {
            val (sp, cut) = name(font, TextSize.Largest, text, "248 games")
            // Measured at 576x432 Largest: no font fits it whole at 20 (Jost needs 423.5 of 364.5 dp).
            assertEquals("$font: steps down to 16", stepped(font, TextSize.Largest), sp, 0.01f)
            // With no battery drawing the status is narrower: at 16 only Jost fits it whole; Sans (since Largest is 1.5), Serif and Mono are ellipsised.
            assertEquals("$font: whole at 16 in Jost, cut in Sans, Serif and Mono ($last)", font != PlumFont.Jost, cut)
            val (medium, mediumCut) = name(font, TextSize.Small, text, "248 games")
            assertEquals("$font Medium: drawn in 20", full(font, TextSize.Small), medium, 0.01f)
            assertFalse("$font Medium: whole at 20", mediumCut)
        }
    }

    @Test
    fun aNameIsNeverCutAt20NorDrawnAt16WhenItFitsAt20() {
        show()
        for (font in PlumFont.entries) {
            for (size in TextSize.entries) {
                for (text in listOf("Homebrew and Translations Collection", "Nintendo Entertainment System", "SNES")) {
                    val (sp, cut) = name(font, size, text, "1,284 games")
                    val atFull = kotlin.math.abs(sp - full(font, size)) < 0.01f
                    assertTrue("$font $size $text: 20 or its step down ($sp)", atFull || kotlin.math.abs(sp - stepped(font, size)) < 0.01f)
                    if (atFull) assertFalse("$font $size $text: never cut at 20", cut)
                }
            }
        }
        // A short name is drawn in 20 even at the largest text size.
        assertEquals(full(PlumFont.Mono, TextSize.Largest), name(PlumFont.Mono, TextSize.Largest, "SNES", "1,284 games").first, 0.01f)
    }

    @Test
    fun theSettingsCrumbKeepsSettingsIn20AndCutsTheDimRest() {
        show()
        for (font in PlumFont.entries) {
            val (sp, cut) = name(font, TextSize.Largest, "Settings", "General › Text size and screen layout")
            assertEquals("$font: Settings in 20", full(font, TextSize.Largest), sp, 0.01f)
            assertFalse("$font: Settings whole ($last)", cut)
        }
    }
}
