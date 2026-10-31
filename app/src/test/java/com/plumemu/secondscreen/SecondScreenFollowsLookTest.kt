package com.plumemu.secondscreen

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.FontSetting
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
 * screen changes exactly like the upper one when choosing colours, theme, font"): through the real
 * window, a change of Colors, Font or Text size in the stored settings redraws the card in that
 * set's background, that font and that size, with no restart.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenFollowsLookTest : SecondScreenHostTestBase() {
    private fun look(
        set: ColorSet,
        font: FontSetting = FontSetting.Sans,
        size: TextSizeSetting = TextSizeSetting.Medium,
    ) = PlumSettings(appearance = AppearanceSettings().withColors(set).copy(font = font, textSize = size, ownColors = null))

    /** The colour most of the window is drawn in: the card's background. */
    private fun backgroundOf(host: SecondScreenHost): Int =
        pixelsOf(host.shownWindow!!).toList().groupingBy { it and 0xFFFFFF }.eachCount().maxBy { it.value }.key

    /**
     * Each text on the card and the style it is laid out with. The window is drawn first: its Compose
     * view measures and lays out when drawn, and Robolectric runs no frame traversal for it, so a
     * change that only moves sizes (Text size, Font) would otherwise not be laid out yet.
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
    fun colorsFontAndTextSizeReachThePickingCard() {
        settings.value = look(ColorSet.Super)
        val host = startHost()
        feed.offer(GAME_WITH_STAR)
        // The feed settles before it shows what is offered.
        idle(500)
        assertEquals("Super's background", ColorSet.Super.backgroundColor.value, backgroundOf(host))
        val medium = textStyles(host)
        assertTrue("the card shows text: $medium", medium.isNotEmpty())

        settings.value = look(ColorSet.Super, size = TextSizeSetting.Largest)
        idle(500)
        val largest = textStyles(host)
        var compared = 0
        for ((text, style) in medium) {
            val grown = largest[text]?.fontSize?.value ?: continue
            val ratio = grown / style.fontSize.value
            assertTrue("'$text' grows with Largest (x$ratio)", abs(ratio - TextSize.Largest.factor) < 0.02f)
            compared++
        }
        assertTrue("the same texts at Medium and Largest: ${medium.keys} / ${largest.keys}", compared > 0)

        settings.value = look(ColorSet.DotMatrix, font = FontSetting.Mono, size = TextSizeSetting.Largest)
        idle(500)
        assertEquals("Dot matrix's background", ColorSet.DotMatrix.backgroundColor.value, backgroundOf(host))
        val mono = PlumTypography(PlumFont.Mono, TextSize.Largest, TextMode.Dark).panelBody.fontFamily
        val inMono = textStyles(host)
        assertTrue("texts to check", inMono.isNotEmpty())
        for ((text, style) in inMono) assertEquals("'$text' in Plum Mono", mono, style.fontFamily)

        settings.value = look(ColorSet.Paper, font = FontSetting.Serif)
        idle(500)
        assertEquals("Paper's background", ColorSet.Paper.backgroundColor.value, backgroundOf(host))
        val serif = PlumTypography(PlumFont.Serif, TextSize.Medium, TextMode.Dark).panelBody.fontFamily
        val inSerif = textStyles(host)
        assertTrue("texts to check", inSerif.isNotEmpty())
        for ((text, style) in inSerif) assertEquals("'$text' in Plum Serif", serif, style.fontFamily)
    }
}
