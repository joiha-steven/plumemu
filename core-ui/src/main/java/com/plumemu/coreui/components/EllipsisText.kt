package com.plumemu.coreui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import kotlin.math.ceil

/**
 * Text cut with "…" the way a title reads right: Compose's own ellipsis can leave the space before the cut
 * ("The Legend of Zelda …"); this one trims it (and a dangling "-" or ":") first. The cut is found by measuring once per
 * text, style, width and line count (a one-slot cache per instance, nothing per frame); text that fits is drawn as it is.
 * Use it for titles and names; a value that never holds a space before its end may keep `TextOverflow.Ellipsis`.
 * It is one node with the whole text and its layout in its semantics, and it answers intrinsic measurements.
 */
@Composable
fun EllipsisText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: (() -> Color)? = null,
    maxLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val cache = remember { CutCache() }
    val policy = EllipsisPolicy(measurer, cache, text, style, maxLines, onTextLayout)
    val semanticsOfText =
        Modifier.semantics {
            this.text = AnnotatedString(text)
            getTextLayoutResult { results -> cache.layout?.let { results.add(it) } != null }
        }
    val drawn = Modifier.drawBehind { cache.layout?.let { drawText(it, color = color?.invoke() ?: Color.Unspecified) } }
    Layout(modifier = modifier.then(semanticsOfText).then(drawn), measurePolicy = policy)
}

private class EllipsisPolicy(
    private val measurer: TextMeasurer,
    private val cache: CutCache,
    private val text: String,
    private val style: TextStyle,
    private val maxLines: Int,
    private val onTextLayout: (TextLayoutResult) -> Unit,
) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val laidOut = cache.layoutAt(measurer, text, style, constraints.maxWidth, maxLines, onTextLayout)
        val width = laidOut.size.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = laidOut.size.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        val baselines: Map<AlignmentLine, Int> = mapOf(FirstBaseline to ceil(laidOut.firstBaseline).toInt(), LastBaseline to ceil(laidOut.getLineBaseline(laidOut.lineCount - 1)).toInt())
        return layout(width, height, alignmentLines = baselines) {}
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = ceil(natural().multiParagraph.maxIntrinsicWidth).toInt()

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = ceil(natural().multiParagraph.minIntrinsicWidth).toInt()

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = heightAt(width)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = heightAt(width)

    private fun natural() = measurer.measure(text, style, overflow = TextOverflow.Clip, maxLines = maxLines, constraints = Constraints())

    private fun heightAt(width: Int) = measurer.measure(text, style, overflow = TextOverflow.Ellipsis, maxLines = maxLines, constraints = Constraints(maxWidth = width)).size.height
}

/** One remembered answer: the last text, style, width and lines asked, and the layout of the cut found for them. */
private class CutCache {
    private var text: String? = null
    private var style: TextStyle? = null
    private var width = -1
    private var maxLines = -1
    var layout: TextLayoutResult? = null
        private set

    fun layoutAt(measurer: TextMeasurer, text: String, style: TextStyle, width: Int, maxLines: Int, report: (TextLayoutResult) -> Unit): TextLayoutResult {
        layout?.let { if (text == this.text && style == this.style && width == this.width && maxLines == this.maxLines) return it }
        val room = Constraints(maxWidth = width)
        val shown =
            if (width == Constraints.Infinity) {
                text
            } else {
                ellipsize(text) { !measurer.measure(it, style, overflow = TextOverflow.Clip, maxLines = maxLines, constraints = room).hasVisualOverflow }
            }
        val made = measurer.measure(shown, style, overflow = TextOverflow.Ellipsis, maxLines = maxLines, constraints = room)
        this.text = text
        this.style = style
        this.width = width
        this.maxLines = maxLines
        layout = made
        report(made)
        return made
    }
}

private const val MARK = "…"

/** Characters that read wrong right before the "…": spaces and a dangling dash, colon, comma, slash or opening bracket. */
private fun isTrailingJunk(c: Char) = c.isWhitespace() || c in "-–—:;,/&([{"

/** [this] without the spaces and dangling punctuation that would read wrong right before a "…". */
fun String.trimBeforeEllipsis(): String = trimEnd(::isTrailingJunk)

/**
 * [text] when [fits] says it does, else its longest prefix that fits with "…" after it; the prefix loses its trailing spaces
 * and dangling punctuation first, and a width that holds only the mark gives the mark alone. [fits] must be monotone: a
 * shorter prefix fits whenever a longer one does.
 */
fun ellipsize(text: String, fits: (String) -> Boolean): String {
    if (fits(text)) return text
    fun candidate(length: Int): String {
        var end = length
        if (end in 1 until text.length && text[end - 1].isHighSurrogate()) end--
        return text.substring(0, end).trimBeforeEllipsis() + MARK
    }
    var low = 0
    var high = text.length - 1
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (fits(candidate(mid))) low = mid else high = mid - 1
    }
    return candidate(low)
}
