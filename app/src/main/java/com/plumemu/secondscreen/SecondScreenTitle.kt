package com.plumemu.secondscreen

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalDensity
import com.plumemu.coreui.components.FAVORITE_STAR
import com.plumemu.coreui.components.trimBeforeEllipsis
import com.plumemu.coreui.theme.SecondScreenDimens
import java.text.BreakIterator

private const val ELLIPSIS = "…"
private const val GAP_ID = "starGap"

/**
 * The title, at most [maxLines] lines, with the favourite star right after its last word. A title
 * too long for the lines is cut with an ellipsis *before* the star, never the star (the games
 * list's rule). The cut is found by measuring, since an ellipsis added by the text itself would
 * come after the star. The width measured is the width the layout gives the title, in pixels, not
 * dp arithmetic: a rounded margin can make the two differ by a pixel and the last line wrap.
 */
@Composable
internal fun SecondScreenTitle(
    title: String,
    isFavorite: Boolean,
    titleStyle: TextStyle,
    starStyle: TextStyle,
    starColor: Color,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val gap = with(density) { SecondScreenDimens.starGap.toSp() }
    BoxWithConstraints {
        val widthPx = constraints.maxWidth
        val shown =
            remember(title, isFavorite, titleStyle, starStyle, starColor, maxLines, widthPx, gap, density) {
                fittedTitleKept(measurer, title, isFavorite, titleStyle, starStyle, starColor, maxLines, widthPx, gap, density.density, density.fontScale)
            }
        BasicText(
            text = shown,
            style = titleStyle,
            inlineContent = mapOf(GAP_ID to InlineTextContent(gapPlaceholder(gap)) {}),
            maxLines = maxLines,
            modifier = modifier,
        )
    }
}

// AboveBaseline: the empty gap stands on the baseline and never sets its line's height. With
// TextBottom it read the text paint's metrics, and a one-line title with its star came out 65 px
// high instead of 63 (Roomy, xhdpi) after another card had been drawn.
private fun gapPlaceholder(gap: TextUnit) = Placeholder(gap, gap, PlaceholderVerticalAlign.AboveBaseline)

private fun withStar(
    text: String,
    isFavorite: Boolean,
    starStyle: TextStyle,
    starColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        append(text)
        if (isFavorite) {
            appendInlineContent(GAP_ID, " ")
            withStyle(SpanStyle(color = starColor, fontSize = starStyle.fontSize, fontWeight = starStyle.fontWeight)) { append(FAVORITE_STAR) }
        }
    }

/** The lines [shown] (a result of [fittedTitle]) takes at [widthPx]; its favourite star is the last character, the gap the one before. */
internal fun linesOf(
    measurer: TextMeasurer,
    shown: AnnotatedString,
    isFavorite: Boolean,
    titleStyle: TextStyle,
    widthPx: Int,
    gap: TextUnit,
): Int {
    val gapAt = shown.text.length - 2
    val ranges = if (isFavorite) listOf(AnnotatedString.Range(gapPlaceholder(gap), gapAt, gapAt + 1)) else emptyList()
    return measurer.measure(shown, titleStyle, constraints = Constraints(maxWidth = widthPx), placeholders = ranges).lineCount
}

/** Offsets in [text] where a person-perceived character starts, and its end: a cut there splits no surrogate pair or accent. */
internal fun characterBoundaries(text: String): List<Int> {
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    val offsets = mutableListOf(0)
    var next = iterator.next()
    while (next != BreakIterator.DONE) {
        offsets += next
        next = iterator.next()
    }
    return offsets
}

/** The title (and star) as drawn: the whole of it if it fits [maxLines] at [widthPx], else the longest cut that does. */
internal fun fittedTitle(
    measurer: TextMeasurer,
    title: String,
    isFavorite: Boolean,
    titleStyle: TextStyle,
    starStyle: TextStyle,
    starColor: Color,
    maxLines: Int,
    widthPx: Int,
    gap: TextUnit,
): AnnotatedString {
    fun build(text: String) = withStar(text, isFavorite, starStyle, starColor)

    fun fits(text: String): Boolean {
        CardLayoutCount.note("titleMeasure")
        return linesOf(measurer, build(text), isFavorite, titleStyle, widthPx, gap) <= maxLines
    }
    if (fits(title)) return build(title)
    val cuts = characterBoundaries(title)

    fun cut(index: Int) = title.substring(0, cuts[index]).trimBeforeEllipsis() + ELLIPSIS
    var low = 0
    var high = cuts.size - 1
    while (low < high) {
        val middle = (low + high + 1) / 2
        if (fits(cut(middle))) low = middle else high = middle - 1
    }
    return build(cut(low))
}

private data class FitKey(
    val title: String,
    val isFavorite: Boolean,
    val titleStyle: TextStyle,
    val starStyle: TextStyle,
    val starColor: Color,
    val maxLines: Int,
    val widthPx: Int,
    val gap: TextUnit,
    val density: Float,
    val fontScale: Float,
)

private val fitted = object : LinkedHashMap<FitKey, AnnotatedString>(TITLES_KEPT, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FitKey, AnnotatedString>) = size > TITLES_KEPT
}

private const val TITLES_KEPT = 32

/** [fittedTitle], remembered for the last titles: a card offered again, or one the person walks back to, measures nothing. */
private fun fittedTitleKept(
    measurer: TextMeasurer,
    title: String,
    isFavorite: Boolean,
    titleStyle: TextStyle,
    starStyle: TextStyle,
    starColor: Color,
    maxLines: Int,
    widthPx: Int,
    gap: TextUnit,
    density: Float,
    fontScale: Float,
): AnnotatedString {
    val key = FitKey(title, isFavorite, titleStyle, starStyle, starColor, maxLines, widthPx, gap, density, fontScale)
    synchronized(fitted) { fitted[key] }?.let { return it }
    return fittedTitle(measurer, title, isFavorite, titleStyle, starStyle, starColor, maxLines, widthPx, gap).also { synchronized(fitted) { fitted[key] = it } }
}

/** Forgets the remembered titles (tests that count measures start from none). */
internal fun forgetFittedTitles() = synchronized(fitted) { fitted.clear() }
