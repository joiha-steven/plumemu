package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.plumemu.coreui.theme.POSTER_BAND_SPACING_EM
import com.plumemu.coreui.theme.POSTER_FRAME_COLOR
import com.plumemu.coreui.theme.POSTER_INK_WHITE
import com.plumemu.coreui.theme.POSTER_SHADOW_COLOR
import com.plumemu.coreui.theme.POSTER_BAND_FLOOR_SP
import com.plumemu.coreui.theme.POSTER_TITLE_FLOOR_SP
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.POSTER_PALETTE
import com.plumemu.coreui.theme.PosterLook
import com.plumemu.coreui.theme.posterLookIndex
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The poster cover of a game without box art: a two-colour gradient, an inner frame, the title large
 * and centred, a darker band along the bottom with the system's name. It fills the size the caller
 * gives it; the caller clips and rounds it. The look follows [title] ([posterLookIndex]).
 * The band shows [system]; when that does not fit at the band's smallest size it shows [systemShort]
 * (for example "SNES"), and if even that does not fit, [systemShort] ending in an ellipsis.
 * The cell around it carries the title for accessibility, so this adds no semantics.
 */
@Composable
fun PosterCover(
    title: String,
    system: String,
    systemShort: String = system,
    modifier: Modifier = Modifier,
) {
    val look = remember(title) { POSTER_PALETTE[posterLookIndex(title)] }
    PosterCover(title, system, systemShort, look, modifier)
}

/** [PosterCover] wearing [look]: tests force every entry of the palette through here. */
@Composable
internal fun PosterCover(
    title: String,
    system: String,
    systemShort: String,
    look: PosterLook,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    // The theme's font family at its heaviest weight; the cover does not follow the text-weight setting.
    val family = PlumTheme.type.gridFocusTitle.fontFamily
    Box(
        modifier.drawWithCache {
            val layout = PosterLayout.of(measurer, this, title, system, systemShort, look, family, size)
            onDrawBehind { layout.draw(this) }
        },
    )
}

/** Where everything of one cover stands at one size, measured once; the brush and stroke are made once too (the focused cover redraws every frame). */
internal class PosterLayout private constructor(
    private val size: Size,
    private val look: PosterLook,
    /** Null when the area above the band holds no line at the smallest size: no title is drawn rather than one over the band or frame. */
    val title: TextLayoutResult?,
    private val titleTopLeft: Offset,
    /** Null when even the smallest band text is taller than the band (tiny covers): no text is drawn rather than one spilling out of the band. */
    val system: TextLayoutResult?,
    private val systemTopLeft: Offset,
) {
    private val bandHeight = size.height * BAND_HEIGHT
    private val short = min(size.width, size.height)
    private val inset = short * FRAME_INSET
    private val brush = Brush.linearGradient(listOf(look.top, look.bottom), start = Offset.Zero, end = Offset(size.width * 0.4f, size.height))
    private val stroke = Stroke(max(1f, short * 0.012f))
    private val frameSize = Size(size.width - inset * 2, size.height - inset * 2)
    private val bandTop = Offset(0f, size.height - bandHeight)
    private val bandSize = Size(size.width, bandHeight)

    fun draw(scope: DrawScope) =
        with(scope) {
            drawRect(brush)
            drawRect(color = POSTER_FRAME_COLOR, topLeft = Offset(inset, inset), size = frameSize, style = stroke)
            drawRect(look.band, topLeft = bandTop, size = bandSize)
            if (system != null) drawText(system, topLeft = systemTopLeft)
            if (title != null) drawText(title, topLeft = titleTopLeft)
        }

    /** Height of the area above the band and inside the frame, where the title must stay. */
    val titleArea: Float get() = size.height - bandHeight - inset * 2

    companion object {
        const val BAND_HEIGHT = 0.11f
        const val FRAME_INSET = 0.045f
        private const val TITLE_WIDTH = 0.80f
        private const val BAND_TEXT_WIDTH = 0.86f
        private val FLOOR = POSTER_TITLE_FLOOR_SP.sp
        private val BAND_FLOOR = POSTER_BAND_FLOOR_SP.sp

        fun of(
            measurer: TextMeasurer,
            density: Density,
            title: String,
            system: String,
            systemShort: String,
            look: PosterLook,
            family: FontFamily?,
            size: Size,
        ): PosterLayout {
            val short = min(size.width, size.height)
            val bandHeight = size.height * BAND_HEIGHT

            // The size never goes under the floor, so a line taller than the band is left out (the band keeps the step-1 proportions).
            val systemLayout = fitBand(measurer, density, system, systemShort, look, family, size.width * BAND_TEXT_WIDTH, bandHeight * 0.42f).takeIf { it.size.height <= bandHeight }
            val systemTopLeft = systemLayout?.let { Offset((size.width - it.size.width) / 2f, size.height - bandHeight + (bandHeight - it.size.height) / 2f) } ?: Offset.Zero

            val maxWidth = (size.width * TITLE_WIDTH).toInt().coerceAtLeast(1)
            val area = size.height - bandHeight - short * FRAME_INSET * 2
            val titleLayout = fitTitle(measurer, density, title.uppercase(Locale.ROOT), look, family, maxWidth, area, short, if (size.width < size.height) 3 else 2, min(size.height * 0.2f, size.width * 0.17f))
            val centre = (size.height - bandHeight) / 2f
            val titleTopLeft = Offset((size.width - maxWidth) / 2f, centre - (titleLayout?.size?.height ?: 0) / 2f)
            return PosterLayout(size, look, titleLayout, titleTopLeft, systemLayout, systemTopLeft)
        }

        private fun bandStyle(
            density: Density,
            look: PosterLook,
            family: FontFamily?,
            sizePx: Float,
        ) = TextStyle(color = look.ink, fontFamily = family, fontWeight = FontWeight.ExtraBold, fontSize = with(density) { sizePx.toSp() }, letterSpacing = POSTER_BAND_SPACING_EM.em)

        /** The full name shrunk to the floor; else the short name shrunk to the floor; else the short name ellipsized inside [maxWidth]. */
        private fun fitBand(
            measurer: TextMeasurer,
            density: Density,
            system: String,
            systemShort: String,
            look: PosterLook,
            family: FontFamily?,
            maxWidth: Float,
            start: Float,
        ): TextLayoutResult {
            val floorPx = with(density) { BAND_FLOOR.toPx() }

            fun measure(
                text: String,
                px: Float,
                bounded: Boolean,
            ) = measurer.measure(
                AnnotatedString(text.uppercase(Locale.ROOT)),
                bandStyle(density, look, family, px),
                overflow = if (bounded) TextOverflow.Ellipsis else TextOverflow.Clip,
                softWrap = false,
                maxLines = 1,
                constraints = if (bounded) Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1)) else Constraints(),
            )

            fun fitting(text: String): TextLayoutResult? {
                var px = max(start, floorPx)
                while (true) {
                    val layout = measure(text, px, bounded = false)
                    if (layout.size.width <= maxWidth) return layout
                    if (px <= floorPx) return null
                    px = max(px * 0.92f, floorPx)
                }
            }
            val shortName = systemShort.ifBlank { system }
            return fitting(system) ?: fitting(shortName) ?: measure(shortName, floorPx, bounded = true)
        }

        /** The title at the largest size that fits [maxLines] without a word broken mid-word; at the floor fewer lines and an ellipsis, or none when no line fits. */
        private fun fitTitle(
            measurer: TextMeasurer,
            density: Density,
            text: String,
            look: PosterLook,
            family: FontFamily?,
            maxWidth: Int,
            area: Float,
            short: Float,
            maxLines: Int,
            start: Float,
        ): TextLayoutResult? {
            val annotated = AnnotatedString(text)
            val floorPx = with(density) { FLOOR.toPx() }
            // White titles get a soft shadow; dark ones none.
            val shadow = if (look.ink == POSTER_INK_WHITE) Shadow(POSTER_SHADOW_COLOR, Offset(0f, short * 0.012f), short * 0.02f) else null

            fun style(px: Float): TextStyle {
                val unit: TextUnit = with(density) { px.toSp() }
                return TextStyle(
                    color = look.ink,
                    fontFamily = family,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = unit,
                    lineHeight = unit * 1.08f,
                    textAlign = TextAlign.Center,
                    shadow = shadow,
                )
            }

            fun measure(
                px: Float,
                lines: Int,
                overflow: TextOverflow,
            ) = measurer.measure(annotated, style(px), overflow = overflow, maxLines = lines, constraints = Constraints(minWidth = maxWidth, maxWidth = maxWidth))

            var px = max(start, floorPx)
            while (true) {
                val layout = measure(px, Int.MAX_VALUE, TextOverflow.Clip)
                if (layout.lineCount <= maxLines && layout.size.height <= area && !breaksWord(layout, text)) return layout
                if (px <= floorPx) break
                px = max(px * 0.93f, floorPx)
            }
            // At the floor: the most lines the area holds (at most maxLines) whose breaks all fall between words, the last one
            // ending in an ellipsis; a single line has no break, so a word wider than the line ends in "..." on its own line.
            val lineHeight = measure(floorPx, 1, TextOverflow.Clip).size.height
            val holds = min(maxLines, floor(area / lineHeight).toInt())
            for (lines in holds downTo 1) {
                val layout = measure(floorPx, lines, TextOverflow.Ellipsis)
                if (lines == 1 || !breaksWord(layout, text)) return layout
            }
            return null
        }

        /** True when a line ends between two letters of one Latin word (CJK breaks between characters by design). */
        internal fun breaksWord(
            layout: TextLayoutResult,
            text: String,
        ): Boolean {
            for (line in 0 until layout.lineCount - 1) {
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
