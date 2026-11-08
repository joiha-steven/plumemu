package com.plumemu.secondscreen

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTypography
import com.plumemu.coreui.theme.TextMode
import com.plumemu.coreui.theme.TextSize
import com.plumemu.settings.withColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The second screen's picking card follows the top screen's look ("the lower
 * screen changes exactly like the upper one when choosing colours and theme"): through the real
 * window, a change of Colors or Text size in the stored settings redraws the card in that
 * set's background and that size, with no restart.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenFollowsLookTest : SecondScreenHostTestBase() {
    private fun look(
        set: ColorSet,
        size: TextSizeSetting = TextSizeSetting.Medium,
    ) = PlumSettings(appearance = AppearanceSettings().withColors(set).copy(textSize = size, ownColors = null))

    /** The colour most of the window is drawn in: the card's background. */
    private fun backgroundOf(host: SecondScreenHost): Int =
        pixelsOf(host.shownWindow!!).toList().groupingBy { it and 0xFFFFFF }.eachCount().maxBy { it.value }.key

    /**
     * Each text on the card and the style it is laid out with. The window is drawn first: its Compose
     * view measures and lays out when drawn, and Robolectric runs no frame traversal for it, so a
     * change that only moves sizes (Text size) would otherwise not be laid out yet.
     */
    private fun textStyles(host: SecondScreenHost): Map<String, androidx.compose.ui.text.TextStyle> {
        pixelsOf(host.shownWindow!!)
        return nodesOf(host.shownWindow!!).mapNotNull { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: return@mapNotNull null
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            layouts.firstOrNull()?.let { text to it.layoutInput.style }
        }.toMap()
    }

    @Test
    fun colorsAndTextSizeReachThePickingCard() {
        settings.value = look(ColorSet.Ivory)
        val host = startHost()
        feed.offer(GAME_WITH_STAR)
        // The feed settles before it shows what is offered.
        idle(500)
        assertEquals("Ivory's background", ColorSet.Ivory.backgroundColor.value, backgroundOf(host))
        val medium = textStyles(host)
        assertTrue("the card shows text: $medium", medium.isNotEmpty())

        settings.value = look(ColorSet.Ivory, size = TextSizeSetting.Largest)
        idle(500)
        val largest = textStyles(host)
        var compared = 0
        for ((text, style) in medium) {
            val grown = largest[text]?.fontSize?.value ?: continue
            val ratio = grown / style.fontSize.value
            assertTrue("'$text' grows with Largest (x$ratio)", abs(ratio - TextSize.Largest.secondScreenFactor / TextSize.Medium.secondScreenFactor) < 0.02f)
            compared++
        }
        assertTrue("the same texts at Medium and Largest: ${medium.keys} / ${largest.keys}", compared > 0)

        settings.value = look(ColorSet.PeaGreen, size = TextSizeSetting.Largest)
        idle(500)
        assertEquals("Pea Green's background", ColorSet.PeaGreen.backgroundColor.value, backgroundOf(host))
        val jost = PlumTypography(PlumFont.Jost, TextSize.Largest, TextMode.Dark).panelBody.fontFamily
        val inJost = textStyles(host)
        assertTrue("texts to check", inJost.isNotEmpty())
        for ((text, style) in inJost) assertEquals("'$text' in Jost", jost, style.fontFamily)

        settings.value = look(ColorSet.Daylight)
        idle(500)
        assertEquals("Paper's background", ColorSet.Daylight.backgroundColor.value, backgroundOf(host))
    }
}
