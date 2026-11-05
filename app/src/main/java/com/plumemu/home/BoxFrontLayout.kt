package com.plumemu.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.plumemu.coreui.theme.BOX_BAND_BOTTOM
import com.plumemu.coreui.components.trimBeforeEllipsis
import com.plumemu.coreui.theme.BOX_BAND_TEXT
import com.plumemu.coreui.theme.BOX_BAND_TOP
import com.plumemu.coreui.theme.BOX_DOTS
import com.plumemu.coreui.theme.POSTER_PALETTE
import com.plumemu.coreui.theme.POSTER_TITLE_FLOOR_SP
import com.plumemu.coreui.theme.PosterDice
import com.plumemu.coreui.theme.PosterLook
import com.plumemu.coreui.theme.boxCapsAlpha
import com.plumemu.coreui.theme.posterHash
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The drawn box front of a game without box art, from the board's "Covers, whole, in a thin collar" CSS (Super Metroid and
 * Contra III): a light band on top with the system's name and three dots, a radial-gradient field, a small line under the
 * band (Publisher · Year), the big title at the bottom left and shapes bleeding in from the sides. Sizes are the board's px
 * scaled by the cover's height over the board's 156 (`u`); title and band text sizes follow the cover's shape between the
 * board's two covers. The field's colours, the shapes and the title's ink come from the title's hash ([PosterDice]).
 * Text is measured once and never clipped by the painter: a long title is cut by hand and ends in "…".
 */
internal class BoxFrontLayout private constructor(
    private val size: Size,
    val look: PosterLook,
    u: Float,
    /** Null when the title's size would fall under [POSTER_TITLE_FLOOR_SP]: no title rather than an unreadable one. */
    val title: TextLayoutResult?,
    val titleTopLeft: Offset,
    val caps: TextLayoutResult?,
    val capsTopLeft: Offset,
    val system: TextLayoutResult?,
    private val systemTopLeft: Offset,
    val shapes: List<BoxShape>,
    private val dots: List<Pair<Color, Offset>>,
    private val dotRadius: Float,
) {
    val bandHeight = BAND * u
    private val field = Rect(0f, bandHeight, size.width, size.height)
    private val fieldBrush =
        run {
            val centre = Offset(field.width * 0.28f, field.top + field.height * 0.18f)
            val radius = hypot(max(centre.x, field.right - centre.x), max(centre.y - field.top, field.bottom - centre.y))
            Brush.radialGradient(0f to look.top, 0.45f to look.bottom, 1f to look.band, center = centre, radius = radius)
        }
    private val bandBrush = Brush.verticalGradient(listOf(BOX_BAND_TOP, BOX_BAND_BOTTOM), startY = 0f, endY = bandHeight)

    fun draw(scope: DrawScope) =
        with(scope) {
            clipRect(0f, 0f, size.width, size.height) {
                drawRect(fieldBrush, topLeft = field.topLeft, size = field.size)
                clipRect(field.left, field.top, field.right, field.bottom) { for (shape in shapes) shape.draw(this) }
                drawRect(bandBrush, size = Size(size.width, bandHeight))
                for ((colour, centre) in dots) drawCircle(colour, dotRadius, centre)
                if (system != null) drawText(system, topLeft = systemTopLeft)
                if (caps != null) drawText(caps, topLeft = capsTopLeft)
                if (title != null) drawText(title, topLeft = titleTopLeft)
            }
        }

    companion object {
        /** The board's cover is 156 px tall with a 25 px band; text and shapes are given in board px. */
        private const val BOARD_HEIGHT = 156f
        private const val BAND = 25f
        private const val DOT = 9f
        private const val DOT_GAP = 4f
        private const val DOTS_RIGHT = 10f
        private const val BAND_TEXT_LEFT = 12f
        private const val CAPS_TOP = 9f
        private const val CAPS_SIZE = 7f
        private const val TITLE_BOTTOM = 10f
        private const val LEFT_SHARE = 0.0705f
        private const val TITLE_LINES = 2
        private const val ELLIPSIS = "…"

        /** The smallest size of the band text and the small line, in sp: a cover too small for it shows none. */
        private const val SMALL_TEXT_FLOOR_SP = 4f
        private const val SHAPE_SALT = 0x5DEECE66DL

        /** The most sizes [fitTitle] tries: 0.93 a step reaches the floor from 20 times it in about 43. */
        private const val FIT_STEPS = 64

        /** The board's two covers: 112 x 156 (title 12, band text 7 with 1 spacing) and 214 x 156 (19, 10, 2). Other shapes lie between or beyond. */
        private const val NARROW_ASPECT = 112f / 156f
        private const val WIDE_ASPECT = 214f / 156f

        fun of(
            measurer: TextMeasurer,
            density: Density,
            title: String,
            system: String,
            systemShort: String,
            line: String,
            family: FontFamily?,
            size: Size,
        ): BoxFrontLayout {
            val u = size.height / BOARD_HEIGHT
            val look = POSTER_PALETTE[PosterDice(posterHash(title)).next(POSTER_PALETTE.size)]
            val t = ((size.width / size.height - NARROW_ASPECT) / (WIDE_ASPECT - NARROW_ASPECT)).coerceIn(0f, 1f)
            val left = size.width * LEFT_SHARE
            val bandHeight = BAND * u
            val smallFloor = with(density) { SMALL_TEXT_FLOOR_SP.sp.toPx() }
            // Measured before it has a size, the shape is 0/0 and every text size NaN, which no floor test catches: no text then.
            val hasSize = size.width > 0f && size.height > 0f && size.width.isFinite() && size.height.isFinite()

            // Band text: the short name when the cover has one (SNES), tracked caps, between the left edge and the dots.
            val bandPx = (7f + 3f * t) * u
            val dotsWidth = (3 * DOT + 2 * DOT_GAP + DOTS_RIGHT) * u
            val systemName = systemShort.ifBlank { system }.uppercase(Locale.ROOT)
            val systemLayout =
                if (!hasSize || systemName.isBlank() || bandPx < smallFloor) {
                    null
                } else {
                    oneLine(measurer, systemName, textStyle(density, family, bandPx, FontWeight.ExtraBold, BOX_BAND_TEXT, (1f + t) * u), size.width - BAND_TEXT_LEFT * u - dotsWidth - 6 * u)
                }
            val systemTopLeft = systemLayout?.let { Offset(BAND_TEXT_LEFT * u, (bandHeight - it.size.height) / 2f) } ?: Offset.Zero

            var dotX = size.width - DOTS_RIGHT * u
            val dots =
                BOX_DOTS.asReversed().map { colour ->
                    dotX -= DOT * u
                    Pair(colour, Offset(dotX + DOT * u / 2f, bandHeight / 2f)).also { dotX -= DOT_GAP * u }
                }

            // The small line, 9 board px under the band.
            val capsPx = CAPS_SIZE * u
            val capsLayout =
                line.uppercase(Locale.ROOT).takeIf { hasSize && it.isNotBlank() && capsPx >= smallFloor }?.let {
                    oneLine(measurer, it, textStyle(density, family, capsPx, FontWeight.Bold, look.ink.copy(alpha = boxCapsAlpha(look))), size.width - left * 2)
                }
            val capsTopLeft = Offset(left, bandHeight + CAPS_TOP * u)
            val capsSize = Size(capsLayout?.size?.width?.toFloat() ?: (size.width - left * 2), capsLayout?.size?.height?.toFloat() ?: (capsPx * 1.2f))

            // The title: at the board's size for this shape; smaller only until no word is broken; two lines at most, then "…".
            val titlePx = (12f + 7f * t) * u
            val titleFloor = with(density) { POSTER_TITLE_FLOOR_SP.sp.toPx() }
            val room = size.height - TITLE_BOTTOM * u - (capsTopLeft.y + capsSize.height) - 3 * u
            val titleLayout =
                if (!hasSize || titlePx < titleFloor || title.isBlank()) {
                    null
                } else {
                    fitTitle(measurer, density, title, family, titlePx, titleFloor, (size.width - left * 2).toInt().coerceAtLeast(1), room, look.ink)
                }
            val titleTopLeft = Offset(left, size.height - TITLE_BOTTOM * u - (titleLayout?.size?.height ?: 0))

            val keepOut = ArrayList<Rect>(2)
            titleLayout?.let { keepOut += Rect(titleTopLeft, Size(it.size.width.toFloat(), it.size.height.toFloat())).inflate(3 * u) }
            keepOut += Rect(capsTopLeft, capsSize).inflate(3 * u)
            val shapes = planShapes(Rect(0f, bandHeight, size.width, size.height), keepOut, look, PosterDice(posterHash(title) xor SHAPE_SALT))
            return BoxFrontLayout(size, look, u, titleLayout, titleTopLeft, capsLayout, capsTopLeft, systemLayout, systemTopLeft, shapes, dots, DOT * u / 2f)
        }

        private fun textStyle(
            density: Density,
            family: FontFamily?,
            px: Float,
            weight: FontWeight,
            color: Color,
            spacingPx: Float = 0f,
            lineHeightPx: Float = 0f,
        ): TextStyle =
            with(density) {
                TextStyle(
                    color = color,
                    fontFamily = family,
                    fontWeight = weight,
                    fontSize = px.toSp(),
                    letterSpacing = if (spacingPx > 0f) spacingPx.toSp() else TextUnit.Unspecified,
                    lineHeight = if (lineHeightPx > 0f) lineHeightPx.toSp() else TextUnit.Unspecified,
                )
            }

        /** Never clips or ellipsizes by itself: [text] wrapped at [maxWidth] (null: one line, any width). */
        private fun measure(
            measurer: TextMeasurer,
            text: String,
            style: TextStyle,
            maxWidth: Int?,
        ): TextLayoutResult =
            measurer.measure(
                AnnotatedString(text),
                style,
                softWrap = maxWidth != null,
                maxLines = if (maxWidth != null) Int.MAX_VALUE else 1,
                constraints = if (maxWidth != null) Constraints(maxWidth = maxWidth) else Constraints(),
            )

        /** [text] on one line no wider than [maxWidth]: cut by hand and ended with "…" when it does not fit. */
        private fun oneLine(
            measurer: TextMeasurer,
            text: String,
            style: TextStyle,
            maxWidth: Float,
        ): TextLayoutResult? {
            val whole = measure(measurer, text, style, null)
            if (whole.size.width <= maxWidth) return whole
            var cut = text.length - 1
            while (cut > 0) {
                cut = safeCut(text, cut)
                if (cut <= 0) break
                val candidate = measure(measurer, text.substring(0, cut).trimBeforeEllipsis() + ELLIPSIS, style, null)
                if (candidate.size.width <= maxWidth) return candidate
                cut--
            }
            return null
        }

        /** [cut] moved back so it does not split a surrogate pair or leave a combining mark (Vietnamese typed as two code points) behind. */
        private fun safeCut(
            text: String,
            cut: Int,
        ): Int {
            var c = cut
            while (c in 1 until text.length && (Character.isLowSurrogate(text[c]) || Character.getType(text[c]) == Character.NON_SPACING_MARK.toInt())) c--
            return c
        }

        /** The title at the board's [startPx] (smaller, down to [floorPx], only while a word is wider than the line or the lines are too tall), two lines at most, the last ending in "…". */
        private fun fitTitle(
            measurer: TextMeasurer,
            density: Density,
            text: String,
            family: FontFamily?,
            startPx: Float,
            floorPx: Float,
            maxWidth: Int,
            room: Float,
            ink: Color,
        ): TextLayoutResult? {
            // Ends on any input: a NaN or infinite size never reaches the floor by shrinking.
            if (!startPx.isFinite() || !floorPx.isFinite() || floorPx <= 0f) return null
            var px = startPx
            repeat(FIT_STEPS) {
                val style = textStyle(density, family, px, FontWeight.ExtraBold, ink, lineHeightPx = px * 1.05f)
                val whole = measure(measurer, text, style, maxWidth)
                val result = if (breaksWord(whole, text)) null else cutToLines(measurer, text, style, maxWidth, whole)
                if (result != null && result.size.height <= room) return result
                if (px <= floorPx) return result ?: oneLine(measurer, text, style, maxWidth.toFloat())
                px = max(px * 0.93f, floorPx)
            }
            return null
        }

        private fun cutToLines(
            measurer: TextMeasurer,
            text: String,
            style: TextStyle,
            maxWidth: Int,
            whole: TextLayoutResult,
        ): TextLayoutResult {
            if (whole.lineCount <= TITLE_LINES) return whole
            var cut = whole.getLineEnd(TITLE_LINES - 1, visibleEnd = true)
            while (cut > 0) {
                cut = safeCut(text, cut)
                if (cut <= 0) break
                val candidate = measure(measurer, text.substring(0, cut).trimBeforeEllipsis() + ELLIPSIS, style, maxWidth)
                if (candidate.lineCount <= TITLE_LINES) return candidate
                cut--
            }
            return measure(measurer, ELLIPSIS, style, maxWidth)
        }

        /** True when a line shown ends between two letters of one Latin word (CJK breaks between characters by design). */
        internal fun breaksWord(
            layout: TextLayoutResult,
            text: String,
        ): Boolean {
            for (line in 0 until min(layout.lineCount - 1, TITLE_LINES - 1)) {
                val end = layout.getLineEnd(line)
                if (end <= 0 || end >= text.length) continue
                val before = text.codePointBefore(end)
                val after = text.codePointAt(end)
                if (Character.isLetterOrDigit(before) && Character.isLetterOrDigit(after) && !isCjk(before) && !isCjk(after)) return true
            }
            return false
        }

        /** Scripts written without spaces, where a line may break between any two characters (ranges: `Character.UnicodeScript` needs API 24). */
        private val CJK_RANGES =
            listOf(
                0x1100..0x11FF, // Hangul Jamo
                0x3005..0x3005, // ideographic iteration mark
                0x3007..0x3007, // ideographic number zero
                0x3040..0x30FF, // Hiragana, Katakana
                0x3130..0x318F, // Hangul compatibility Jamo
                0x31F0..0x31FF, // Katakana extensions
                0x3400..0x4DBF, // CJK extension A
                0x4E00..0x9FFF, // CJK unified
                0xAC00..0xD7AF, // Hangul syllables
                0xF900..0xFAFF, // CJK compatibility
                0xFF00..0xFFEF, // full-width and half-width forms (half-width kana FF61-FF9F)
                0x20000..0x2FFFF, // CJK extensions B and later
            )

        private fun isCjk(codePoint: Int): Boolean = CJK_RANGES.any { codePoint in it }
    }
}
