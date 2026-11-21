package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PLACE_NAME_STEP_SP
import com.plumemu.coreui.theme.TypeRole

/**
 * The top bar's "where you are": the [emphasis] (the system's name) bold and bright, then the rest ("248 games") small and dim,
 * on one baseline, [PlumDimens.placeCountGap] apart, as the board draws it. The separator the caller wrote between them is not drawn.
 * Text without the [emphasis] is drawn all dim. The name is measured first: a count that does not fit whole beside it is left out, never cut. Colours are read while drawing.
 * The name is drawn in its size (20) when its whole line fits the room the right-hand items leave, else one ramp step down
 * ([PLACE_NAME_STEP_DOWN], 16), and only then ellipsised ([placeNameStyle]). [placeWithTail], the same place with words after it
 * (a list's sort, "· Recently added"), is drawn instead only when its whole line fits: the tail goes before the name or count is cut.
 */
@Composable
internal fun PlaceText(
    place: String,
    emphasis: String,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
    /** The name in the top bar's size and line (a keyboard tray is up and the bar keeps its base height). */
    compact: Boolean = false,
    /** [place] with a tail that is left out rather than cut; null for none. */
    placeWithTail: String? = null,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val parts = remember(place, emphasis) { splitPlace(place, emphasis) }
    val tailed = remember(placeWithTail, emphasis) { placeWithTail?.let { splitPlace(it, emphasis) } }
    val name: () -> Color = { colors.text }
    val count: () -> Color = { colors.muted }
    val measurer = rememberTextMeasurer()
    // The 20 name's line is taller than the bar (which holds the 16 step's): it reaches into the margins around it, centred, never cut.
    BoxWithConstraints(modifier.wrapContentHeight(unbounded = true).graphicsLayer { this.alpha = alpha() }) {
        val nameStyle =
            if (compact) {
                type.placeName.copy(fontSize = type.meta.fontSize, lineHeight = type.meta.lineHeight)
            } else {
                placeNameStyle(type.placeName) { measurer.measure(parts.name, it, maxLines = 1).size.width <= constraints.maxWidth }
            }
        val gap = with(LocalDensity.current) { PlumDimens.placeCountGap.roundToPx() }
        // The rest is drawn whole or not at all (a count cut to "1 234 j…" reads as broken): the tailed place when its whole
        // line fits, else the place, else the name alone.
        fun fitsWhole(it: PlaceParts): Boolean {
            val nameWidth = if (it.name.isEmpty()) 0 else measurer.measure(it.name, nameStyle, maxLines = 1).size.width + gap
            return nameWidth + measurer.measure(it.rest, restStyle(it.rest, type.placeCount, type.placeWords), maxLines = 1).size.width <= constraints.maxWidth
        }
        val shown = tailed?.takeIf(::fitsWhole) ?: parts
        val restShown = shown.rest.isNotEmpty() && (shown.name.isEmpty() || fitsWhole(shown))
        Row(horizontalArrangement = Arrangement.spacedBy(PlumDimens.placeCountGap)) {
            if (shown.name.isNotEmpty()) {
                BasicText(text = shown.name, style = nameStyle, color = name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline())
            }
            if (restShown) {
                BasicText(text = shown.rest, style = restStyle(shown.rest, type.placeCount, type.placeWords), color = count, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).alignByBaseline())
            }
        }
    }
}

/** A count (it has a digit: "248 games") is a phrase: Jost with tabular figures ([count]); other words (Settings' "› Rules") are plain Jost ([words]). */
private fun restStyle(rest: String, count: TextStyle, words: TextStyle): TextStyle = if (rest.any(Char::isDigit)) count else words

/** The place name's one step down when its whole line does not fit at its own size: the ramp's 16 (before font and text size scaling). */
internal const val PLACE_NAME_STEP_DOWN = PLACE_NAME_STEP_SP

/**
 * [full] when [fits] says its whole line fits, else the same weight one ramp step down (scaled as [full] is, line height kept so the bar
 * does not move); the caller ellipsises what still does not fit.
 */
internal fun placeNameStyle(
    full: TextStyle,
    fits: (TextStyle) -> Boolean,
): TextStyle {
    if (fits(full)) return full
    val ratio = PLACE_NAME_STEP_DOWN / TypeRole.PlaceName.sizeSp
    return full.copy(fontSize = (full.fontSize.value * ratio).sp)
}

/** [place] cut at its first [emphasis]: the words before and after are the dim rest. */
internal data class PlaceParts(
    val name: String,
    val rest: String,
)

internal fun splitPlace(
    place: String,
    emphasis: String,
): PlaceParts {
    val start = if (emphasis.isEmpty()) -1 else place.indexOf(emphasis)
    if (start < 0) return PlaceParts("", place)
    val before = place.substring(0, start).trim { it.isWhitespace() || it in SEPARATORS }
    val after = place.substring(start + emphasis.length).trim { it.isWhitespace() || it in SEPARATORS }
    return PlaceParts(emphasis, listOf(before, after).filter { it.isNotEmpty() }.joinToString(" "))
}

private const val SEPARATORS = "·•|›"
