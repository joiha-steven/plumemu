package com.plumemu.coreui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * The second screen's in-game card, Clock and battery (design/rules.md › Second screen › In game). Fixed sizes and font: neither the text size setting,
 * Android's font scale nor the person's font applies, because the burn-in numbers were measured on
 * exactly these. So the text sizes are in dp, turned into sp with the font scale taken out.
 *
 * The battery part under it takes
 * its sizes from the second screen's type ramp ([leftRole], [unitRole], [lineRole]) and follows the
 * person's font and Text size like the rest of the second screen, but not Android's font scale
 * ([partStyle]): the lit area was measured at these sizes.
 * Compact is Roomy one ramp step down, its gaps and strokes scaled about as the clock (60 / 84).
 */
@Immutable
enum class InGameDimens(
    val timeSize: Dp,
    val batterySize: Dp,
    /** The battery glyph's size against Roomy's (the glyph follows the battery line's text). */
    val glyphScale: Float,
    /** The battery part: the time left's number and its words. */
    val leftRole: TypeRole,
    val unitRole: TypeRole,
    /** The battery part's frame: nothing nearer than 20 dp to an edge at any point of the drift (+-4 x +-12). */
    val partInsetTop: Dp,
    val partInsetSide: Dp,
    val partInsetBottom: Dp,
    /** Between the clock card and the time left. */
    val cardToPartGap: Dp,
    /** Between the head (the level ring, the time left) and the chart. */
    val headToChartGap: Dp,
    /** The least room between the chart's labels and the facts row, and between the facts and the drift note. */
    val labelsToKeyGap: Dp,
    /** The level ring left of the time left: its radius (to the stroke's middle), stroke, and the gap to the time left. */
    val ringRadius: Dp,
    val ringStroke: Dp,
    val ringGap: Dp,
    /** The chart's height: as much as is left up to [chartMaxHeight]; under [chartMinHeight] the key gives way. */
    val chartMaxHeight: Dp,
    val chartMinHeight: Dp,
    /** The chart's lines: this play (thicker), the line ahead (fading along its length), the usual pace; the dot at now. */
    val playedStroke: Dp,
    val aheadStroke: Dp,
    val usualStroke: Dp,
    val nowDotRadius: Dp,
) {
    Roomy(
        84.dp, 20.dp, 1f,
        leftRole = TypeRole.SecondBatteryLeftRoomy, unitRole = TypeRole.SecondBatteryUnitRoomy,
        partInsetTop = 32.dp, partInsetSide = 40.dp, partInsetBottom = 32.dp,
        cardToPartGap = 28.dp, headToChartGap = 16.dp, labelsToKeyGap = 10.dp,
        ringRadius = 30.dp, ringStroke = 2.dp, ringGap = 20.dp,
        chartMaxHeight = 144.dp, chartMinHeight = 64.dp,
        playedStroke = 3.dp, aheadStroke = 2.dp, usualStroke = 2.dp, nowDotRadius = 5.dp,
    ),
    Compact(
        60.dp, 16.dp, 0.8f,
        leftRole = TypeRole.SecondBatteryLeftCompact, unitRole = TypeRole.SecondBatteryUnitCompact,
        partInsetTop = 32.dp, partInsetSide = 28.dp, partInsetBottom = 32.dp,
        cardToPartGap = 20.dp, headToChartGap = 10.dp, labelsToKeyGap = 6.dp,
        ringRadius = 22.dp, ringStroke = 1.5.dp, ringGap = 14.dp,
        chartMaxHeight = 104.dp, chartMinHeight = 48.dp,
        playedStroke = 2.5.dp, aheadStroke = 1.5.dp, usualStroke = 1.5.dp, nowDotRadius = 4.dp,
    ),
    ;

    /** The time: Plum Sans 400, tabular digits, so the block does not jitter as the minutes change. */
    fun timeStyle(density: Density): TextStyle = styleOf(fixedSp(timeSize, density), TIME_LINE_HEIGHT_EM)

    fun batteryStyle(density: Density): TextStyle = styleOf(fixedSp(batterySize, density), PlumFont.Sans.lineHeightEm)

    /**
     * A battery part style: [role]'s size in [font] at [textSize] as the rest of the second screen
     * ([TypeRole.scaledSizeSp]), in dp so Android's font scale leaves it, tabular digits.
     */
    fun partStyle(
        role: TypeRole,
        font: PlumFont,
        textSize: TextSize,
        density: Density,
    ): TextStyle {
        val size = fixedSp(role.scaledSizeSp(font, textSize, secondScreen = true).dp, density)
        return TextStyle(
            fontFamily = PlumFonts.familyOf(font),
            fontSize = size,
            fontWeight = FontWeight(role.weight),
            fontFeatureSettings = "tnum",
            lineHeight = size * font.lineHeightEm,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
    }

    companion object {
        /** The same size class as the picking cards ([SecondScreenDimens.forShorterSide]). */
        fun forShorterSide(shorterSide: Dp): InGameDimens =
            if (shorterSide >= SecondScreenDimens.ROOMY_MIN_SHORT_SIDE) Roomy else Compact

        /** Between the time and the battery line. */
        val timeToBatteryGap: Dp = 8.dp

        /** The battery part's other lines at both sizes: "empty at about 22:35", the ring's level, the chart's labels, the facts, the notes. */
        val lineRole: TypeRole = TypeRole.SecondBatteryLine

        /** Between the chart and its labels; the whole hours' ticks, short, a gap under the chart's floor (no grid, no baseline). */
        val chartToLabelsGap: Dp = 4.dp
        val tickGap: Dp = 6.dp
        val tickLength: Dp = 4.dp
        val tickStroke: Dp = 1.5.dp

        /** The chart's lines keep this far from its top and bottom, so 100% and 0% are not cut by their stroke. */
        val chartPad: Dp = 4.dp

        /** Inside the level ring, between its stroke and the number. */
        val ringPad: Dp = 6.dp

        /** The ring at the chart's right end (empty or full), and the faint dotted guide down from now. */
        val endMarkRadius: Dp = 5.dp
        val endMarkStroke: Dp = 2.dp
        val nowGuideStroke: Dp = 1.5.dp
        val nowGuideGap: Dp = 6.dp

        /** The usual pace's dots: a round cap every [usualDotGap]. */
        val usualDotGap: Dp = 7.dp

        /** The least room between the chart's labels ("Started 21:07", "Empty 23:15"), and between the facts. */
        val labelGap: Dp = 8.dp

        /** The facts row's pieces: "Played 0:44 · Used 6% · 8% an hour", this gap each side of the separator. */
        val factGap: Dp = 8.dp

        /** The battery glyph at Roomy (the mockup's 26 x 13 cell, 2 thick, a 3 x 5 nub); Compact scales it by [glyphScale]. */
        val glyphBodyWidth: Dp = 26.dp
        val glyphBodyHeight: Dp = 13.dp
        val glyphStroke: Dp = 2.dp
        val glyphCorner: Dp = 3.dp
        val glyphNubWidth: Dp = 3.dp
        val glyphNubHeight: Dp = 5.dp

        /** Between the outline and its fill, inside the stroke. */
        val glyphFillInset: Dp = 2.dp

        /** Between the glyph (with its nub) and the percentage. */
        val glyphGap: Dp = 6.dp

        /** Between the Wi-Fi glyph and the battery glyph, on the battery line. */
        val wifiGap: Dp = 10.dp

        /**
         * One step of the pixel shift: the card moves this far each minute.
         * On a 5 x 7 grid, 2 dp sideways and 4 dp up or down keep it within 4 dp sideways and
         * 12 dp up or down of the centre (centred, drifting, mostly up and down).
         */
        val shiftStepX: Dp = 2.dp
        val shiftStepY: Dp = 4.dp

        /** The time's line, tighter than the font's own (the mockup's 88 for 84), so the battery line sits close under the digits. */
        const val TIME_LINE_HEIGHT_EM = 88f / 84f

        /** [size] as sp that draws [size] dp whatever Android's font scale (`Dp.toSp` divides by it). */
        private fun fixedSp(
            size: Dp,
            density: Density,
        ): TextUnit = with(density) { size.toSp() }

        private fun styleOf(
            size: TextUnit,
            lineHeightEm: Float,
        ): TextStyle =
            TextStyle(
                fontFamily = PlumFonts.familyOf(PlumFont.Sans),
                fontSize = size,
                fontWeight = FontWeight.Normal,
                fontFeatureSettings = "tnum",
                lineHeight = size * lineHeightEm,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            )
    }
}

/**
 * The in-game card's colours: dim on black whatever background the person picked (an OLED stays
 * mostly off; the clock card nothing brighter than [time], about 13% of white's luminance). The
 * battery part under it is a step brighter ([partText], #767676, 18%): small text must read at
 * 4.6:1 (the design's target). With the chart's soft fill it lights up to about 13% of the screen, at
 * a mean brightness under 0.5% (the burn-in budget).
 */
@Immutable
object InGameColors {
    val background: Color = Color(0xFF000000)
    val time: Color = Color(0xFF646464)
    val battery: Color = Color(0xFF555555)

    /** The battery line at 15% and below, not charging: a dim red, no brighter than [time]. */
    val batteryLow: Color = Color(0xFF8E3740)

    /**
     * The battery part: its words and this play's line, 4.6:1 on black (small
     * text, WCAG AA); the time left and the line at 15% and below, not charging, 3.3:1 (large text
     * only); the line ahead 3.1:1 where it starts, fading along its length; the usual pace 2.8:1;
     * the hour ticks, the ring's track and the now guide; the mark at the chart's right end.
     */
    val partText: Color = Color(0xFF767676)
    val partLow: Color = Color(0xFFA0404A)
    val ahead: Color = Color(0xFF5C5C5C)
    val usual: Color = Color(0xFF555555)
    val tick: Color = Color(0xFF333333)
    val endMark: Color = Color(0xFF3A3A3A)

    /** The glow round the chart's dot at now: this play's line colour at this alpha in its middle, none at its edge. */
    const val NOW_GLOW_ALPHA = 0.25f

    /** The area under this play's line: its colour at this alpha at the line, none at the chart's floor. */
    const val AREA_ALPHA = 0.12f

    /** The area under the line ahead: this alpha where it starts, none at its end. */
    const val AHEAD_AREA_ALPHA = 0.04f

    /** The line ahead ends at this share of its colour's alpha. */
    const val AHEAD_FADE_TO = 0.3f
}
