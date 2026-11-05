package com.plumemu.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.plumemu.coreui.theme.POSTER_BAND_FLOOR_SP
import com.plumemu.coreui.theme.POSTER_BAND_SPACING_EM
import com.plumemu.coreui.theme.POSTER_FRAME_COLOR
import com.plumemu.coreui.theme.PosterLook
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * What an app game's card draws behind its icon: a two-colour gradient, a frame, and a darker band along the bottom holding
 * the system's name. (Games without box art wear the drawn box front instead, [BoxFrontLayout].) The band shows the full
 * name; when that does not fit at the band's smallest size, the short name, then the short name ending in an ellipsis.
 */
internal class AppCardLayout private constructor(
    private val size: Size,
    private val look: PosterLook,
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
        }

    companion object {
        const val BAND_HEIGHT = 0.11f
        const val FRAME_INSET = 0.045f
        private const val BAND_TEXT_WIDTH = 0.86f
        private val BAND_FLOOR = POSTER_BAND_FLOOR_SP.sp

        fun of(
            measurer: TextMeasurer,
            density: Density,
            system: String,
            systemShort: String,
            look: PosterLook,
            family: FontFamily?,
            size: Size,
        ): AppCardLayout {
            val bandHeight = size.height * BAND_HEIGHT
            // The size never goes under the floor, so a line taller than the band is left out.
            val systemLayout = fitBand(measurer, density, system, systemShort, look, family, size.width * BAND_TEXT_WIDTH, bandHeight * 0.42f).takeIf { it.size.height <= bandHeight }
            val systemTopLeft = systemLayout?.let { Offset((size.width - it.size.width) / 2f, size.height - bandHeight + (bandHeight - it.size.height) / 2f) } ?: Offset.Zero
            return AppCardLayout(size, look, systemLayout, systemTopLeft)
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
    }
}
