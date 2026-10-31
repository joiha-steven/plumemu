package com.plumemu.secondscreen

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints

private const val ELLIPSIS = "\u2026"

/**
 * The facts line as one text. A name that is too long is cut by measuring, so what follows it
 * stays and sits right after the ellipsis like after any word; a line too long even without the
 * name is cut at its end by the text's own ellipsis.
 */
@Composable
internal fun FactsLine(
    facts: Facts,
    style: TextStyle,
    modifier: Modifier = Modifier,
    tag: String = SecondScreenTags.FACTS,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints {
        val widthPx = constraints.maxWidth
        val shown = remember(facts, style, widthPx) { fittedFacts(measurer, facts, style, widthPx) }
        BasicText(text = shown, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier.testTag(tag))
    }
}

/** [facts] as drawn on one line at [widthPx]: the whole line, or with the name cut by the most characters that fit. */
internal fun fittedFacts(
    measurer: TextMeasurer,
    facts: Facts,
    style: TextStyle,
    widthPx: Int,
): AnnotatedString {
    fun build(name: String?) =
        buildAnnotatedString {
            append(facts.before)
            if (name != null) append(name)
            append(facts.after)
        }

    fun fits(line: AnnotatedString) = measurer.measure(line, style, constraints = Constraints(maxWidth = widthPx)).lineCount <= 1
    val name = facts.cuttable ?: return build(null)
    val whole = build(name)
    if (fits(whole)) return whole
    val cuts = characterBoundaries(name)

    fun cut(index: Int) = build(name.substring(0, cuts[index]).trimEnd() + ELLIPSIS)
    // Not even the ellipsis fits: keep the whole line and let the text's own ellipsis end it.
    if (!fits(cut(0))) return whole
    var low = 0
    var high = cuts.size - 1
    while (low < high) {
        val middle = (low + high + 1) / 2
        if (fits(cut(middle))) low = middle else high = middle - 1
    }
    return cut(low)
}
