package com.plumemu.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.plumemu.coreui.components.trimBeforeEllipsis
import com.plumemu.coreui.theme.BOX_GRAIN
import com.plumemu.coreui.theme.BOX_MARK_ALPHA
import com.plumemu.coreui.theme.BoxFieldLook
import com.plumemu.coreui.theme.POSTER_TITLE_FLOOR_SP
import com.plumemu.coreui.theme.PlumFonts
import java.util.Locale
import kotlin.math.max
import kotlin.random.Random
import kotlin.math.min

/** Test-only counters: how many card images were built and how many times one was drawn. Null in the app. */
internal class BoxFrontProbe {
    var builds = 0
    var imageDraws = 0
}

internal var boxFrontProbe: BoxFrontProbe? = null

/**
 * The drawn box front of a game without box art: a card in the system's colour (a light to dark diagonal, [BOX_GRAIN] grain like
 * the cover pictures', a big faint first letter); the system's short code (Mono 500, white at 80 %) sits at the top left and
 * the title (Jost 700, white, up to three lines) at the bottom left. Sizes are the board's px scaled by the cover's height over
 * the board's 156 (`u`); the title and the code follow the cover's shape between the board's two covers. Text is measured once
 * and never clipped by the painter: a long title is cut by hand and ends in "…". The card is drawn once into an image
 * ([render]); the screen only draws that image.
 */
internal class BoxFrontLayout private constructor(
    val size: Size,
    private val look: BoxFieldLook,
    private val seed: Long,
    /** Null when the title's size would fall under [POSTER_TITLE_FLOOR_SP]: no title rather than an unreadable one. */
    val title: TextLayoutResult?,
    val titleTopLeft: Offset,
    /** The system's short code; null when the cover is too small for [SMALL_TEXT_FLOOR_SP]. */
    val code: TextLayoutResult?,
    val codeTopLeft: Offset,
    /** The title's first letter, drawn large at the bottom right and cut by the card's edges; null when the title has none. */
    val mark: TextLayoutResult?,
    val markTopLeft: Offset,
) {
    private val fieldBrush =
        Brush.linearGradient(
            0f to look.top,
            1f to look.bottom,
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )

    private fun drawField(scope: DrawScope) = scope.drawRect(fieldBrush)

    private fun drawTexts(scope: DrawScope) =
        with(scope) {
            if (mark != null) drawText(mark, topLeft = markTopLeft)
            if (code != null) drawText(code, topLeft = codeTopLeft)
            if (title != null) drawText(title, topLeft = titleTopLeft)
        }

    /** Fine monochrome noise over the field, the cover pictures' grain family: the same for the same title and size. */
    private fun addGrain(image: ImageBitmap) {
        val bitmap = image.asAndroidBitmap()
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val random = Random(seed)
        for (i in pixels.indices) {
            val n = random.nextInt(-BOX_GRAIN, BOX_GRAIN + 1)
            val c = pixels[i]
            val r = (((c shr 16) and 0xFF) + n).coerceIn(0, 255)
            val g = (((c shr 8) and 0xFF) + n).coerceIn(0, 255)
            val b = ((c and 0xFF) + n).coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b // the field is opaque
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /** The card as an image of exactly its size: built once, then drawn as is. Null before the cover has a size. */
    fun render(density: Density): ImageBitmap? {
        if (!hasSize(size)) return null
        val image = ImageBitmap(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
        val canvas = Canvas(image)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, size) { drawField(this) }
        addGrain(image)
        // The texts go on after the grain: crisp letters on a grained field.
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, size) { drawTexts(this) }
        boxFrontProbe?.let { it.builds++ }
        return image
    }

    companion object {
        /** The board's cover is 156 px tall; text is given in board px. */
        private const val BOARD_HEIGHT = 156f
        private const val CODE_TOP = 10f
        /** The code is a label: this share of the title's size. */
        private const val CODE_OF_TITLE = 0.62f
        private const val MARK_HEIGHT = 0.9f
        private const val MARK_BLEED = 0.1f
        private const val TITLE_BOTTOM = 10f
        private const val LEFT_SHARE = 0.0705f
        private const val TITLE_LINES = 3
        private const val ELLIPSIS = "…"

        /** The smallest size of the code, in sp: a cover too small for it shows none. */
        private const val SMALL_TEXT_FLOOR_SP = 4f

        /** The most sizes [fitTitle] tries: 0.93 a step reaches the floor from 20 times it in about 43. */
        private const val FIT_STEPS = 64

        /** The board's two covers: 112 x 156 (title 12, code 7) and 214 x 156 (19, 10). Other shapes lie between or beyond. */
        private const val NARROW_ASPECT = 112f / 156f
        private const val WIDE_ASPECT = 214f / 156f

        /** Measured before it has a size, the shape is 0/0 and every text size NaN, which no floor test catches: no text then. */
        private fun hasSize(size: Size) = size.width > 0f && size.height > 0f && size.width.isFinite() && size.height.isFinite()

        private fun seedOf(title: String, size: Size): Long = title.hashCode().toLong() * 31 + size.width.toLong() * 7919 + size.height.toLong()

        /** Only the field and its grain, no text and no letter: the titled tile's ground (the card's own drawing, so the two never drift apart). */
        fun fieldOnly(
            title: String,
            look: BoxFieldLook,
            size: Size,
        ): BoxFrontLayout = BoxFrontLayout(size, look, seedOf(title, size), null, Offset.Zero, null, Offset.Zero, null, Offset.Zero)

        fun of(
            measurer: TextMeasurer,
            density: Density,
            title: String,
            system: String,
            systemShort: String,
            look: BoxFieldLook,
            size: Size,
        ): BoxFrontLayout {
            val u = size.height / BOARD_HEIGHT
            val t = ((size.width / size.height - NARROW_ASPECT) / (WIDE_ASPECT - NARROW_ASPECT)).coerceIn(0f, 1f)
            val left = size.width * LEFT_SHARE
            val smallFloor = with(density) { SMALL_TEXT_FLOOR_SP.sp.toPx() }
            val hasSize = hasSize(size)

            val titlePx = (12f + 7f * t) * u
            val codePx = CODE_OF_TITLE * titlePx
            val codeText = systemShort.ifBlank { system }.uppercase(Locale.ROOT)
            val codeLayout =
                if (!hasSize || codeText.isBlank() || codePx < smallFloor) {
                    null
                } else {
                    oneLine(measurer, codeText, textStyle(density, PlumFonts.Mono, codePx, FontWeight.Medium, look.code), size.width - left * 2)
                }
            val codeTopLeft = Offset(left, CODE_TOP * u)
            val codeBottom = codeTopLeft.y + (codeLayout?.size?.height ?: 0)

            // The title: at the board's size for this shape; smaller only until no word is broken; three lines at most, then "…".
            val titleFloor = with(density) { POSTER_TITLE_FLOOR_SP.sp.toPx() }
            val room = size.height - TITLE_BOTTOM * u - codeBottom - 3 * u
            val titleLayout =
                if (!hasSize || titlePx < titleFloor || title.isBlank()) {
                    null
                } else {
                    fitTitle(measurer, density, title, titlePx, titleFloor, (size.width - left * 2).toInt().coerceAtLeast(1), room, look.title)
                }
            val titleTopLeft = Offset(left, size.height - TITLE_BOTTOM * u - (titleLayout?.size?.height ?: 0))
            val mark =
                markOf(title)?.takeIf { hasSize }?.let { letter ->
                    // Jost's capitals stand about 0.7 em tall: the size that makes the letter 0.9 of the shorter side.
                    val side = min(size.width, size.height)
                    measure(measurer, letter, textStyle(density, PlumFonts.Jost, side * MARK_HEIGHT / 0.7f, FontWeight.ExtraBold, look.mark.copy(alpha = BOX_MARK_ALPHA)), null)
                }
            val markTopLeft =
                mark?.let {
                    val bleed = min(size.width, size.height) * MARK_BLEED
                    Offset(size.width + bleed - it.size.width, size.height + bleed - it.firstBaseline)
                } ?: Offset.Zero
            return BoxFrontLayout(size, look, seedOf(title, size), titleLayout, titleTopLeft, codeLayout, codeTopLeft, mark, markTopLeft)
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

        /** The title at the board's [startPx] (smaller, down to [floorPx], only while a word is wider than the line or the lines are too tall), three lines at most, the last ending in "…". */
        private fun fitTitle(
            measurer: TextMeasurer,
            density: Density,
            text: String,
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
                val style = textStyle(density, PlumFonts.Jost, px, FontWeight.Bold, ink, lineHeightPx = px * 1.05f)
                val whole = measure(measurer, text, style, maxWidth)
                val result = if (breaksWord(whole, text)) null else balanced(measurer, text, style, maxWidth, cutToLines(measurer, text, style, maxWidth, whole))
                if (result != null && result.size.height <= room) return result
                if (px <= floorPx) return result ?: oneLine(measurer, text, style, maxWidth.toFloat())
                px = max(px * 0.93f, floorPx)
            }
            return null
        }

        /** [text]'s first letter or digit in capitals, after a leading "The ": the card's big letter; null when it has none. */
        internal fun markOf(text: String): String? {
            val trimmed = text.trim()
            val body = if (trimmed.length > 4 && trimmed.startsWith("the ", ignoreCase = true)) trimmed.substring(4) else trimmed
            var i = 0
            while (i < body.length) {
                val cp = body.codePointAt(i)
                if (Character.isLetterOrDigit(cp)) return String(Character.toChars(cp)).uppercase(Locale.ROOT)
                i += Character.charCount(cp)
            }
            return null
        }

        /** [result] with its line breaks moved, when it is whole and has several lines, to the most even ones (see [balance]). */
        private fun balanced(
            measurer: TextMeasurer,
            text: String,
            style: TextStyle,
            maxWidth: Int,
            result: TextLayoutResult,
        ): TextLayoutResult {
            if (result.lineCount < 2 || result.layoutInput.text.text != text) return result
            val broken = balance(measurer, text, style, maxWidth, result.lineCount) ?: return result
            return measure(measurer, broken, style, maxWidth)
        }

        /**
         * [text] with newlines at the breaks, in [lines] lines each within [maxWidth], that make the lines most even (the widest
         * line as narrow as it can be, then the least spread); a break that leaves a last line of two characters or fewer ("2")
         * is taken only when there is no other. Null when no choice of breaks fits.
         */
        internal fun balance(
            measurer: TextMeasurer,
            text: String,
            style: TextStyle,
            maxWidth: Int,
            lines: Int,
        ): String? {
            val words = text.trim().split(' ').filter { it.isNotEmpty() }
            if (lines < 2 || lines > TITLE_LINES || words.size < lines) return null
            val widths = HashMap<Pair<Int, Int>, Int>()

            fun width(
                from: Int,
                to: Int,
            ) = widths.getOrPut(from to to) { measure(measurer, words.subList(from, to).joinToString(" "), style, null).size.width }

            // Every way to cut the words into `left` runs, as the end of each run.
            fun cuts(
                from: Int,
                left: Int,
            ): List<List<Int>> =
                if (left == 1) {
                    listOf(listOf(words.size))
                } else {
                    (from + 1..words.size - left + 1).flatMap { end -> cuts(end, left - 1).map { listOf(end) + it } }
                }
            val choices = cuts(0, lines).map { ends -> (listOf(0) + ends).zipWithNext() }.filter { runs -> runs.all { (a, b) -> width(a, b) <= maxWidth } }
            if (choices.isEmpty()) return null

            fun lastLength(runs: List<Pair<Int, Int>>) = words.subList(runs.last().first, runs.last().second).joinToString(" ").length
            val roomy = choices.filter { lastLength(it) > 2 }.ifEmpty { choices }
            val best =
                roomy.minWith(
                    compareBy<List<Pair<Int, Int>>> { runs -> runs.maxOf { (a, b) -> width(a, b) } }
                        .thenBy { runs ->
                            val ws = runs.map { (a, b) -> width(a, b).toDouble() }
                            val mean = ws.average()
                            ws.sumOf { (it - mean) * (it - mean) }
                        },
                )
            return best.joinToString("\n") { (a, b) -> words.subList(a, b).joinToString(" ") }
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
