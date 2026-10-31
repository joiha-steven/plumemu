package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import java.text.BreakIterator
import java.util.Locale

/**
 * The title of a text tile (a cover without a picture) made to wrap between words (seen on an AYN Thor:
 * "Treasur" / "e", then "Treas…" in the middle of a title). Compose breaks a word that does not
 * fit the tile's width in two, so the title is fitted before it is laid out: the title's type steps down
 * [styles] (the grid's title style, then the ramp's smallest tile step) until every word fits; a word that
 * still does not fit at the last step is cut to a prefix and "…", and nothing follows it.
 *
 * Words are the line-break opportunities of `BreakIterator.getLineInstance` (CJK breaks between characters,
 * "Spider-Man" after the hyphen); a cut falls on a grapheme boundary (`getCharacterInstance`), never inside a
 * surrogate pair or before a combining mark. One instance is shared by a grid's tiles with its text measurer
 * and keeps its answers by (title, styles, width).
 */
internal class TileTitleFit(
    val measurer: TextMeasurer,
) {
    /** The answer for one title: [text] to draw in the style at [step] of the styles asked for. */
    data class Fitted(
        val text: String,
        val step: Int,
    )

    private data class Key(
        val title: String,
        val styles: List<TextStyle>,
        val widthPx: Int,
    )

    private val answers = HashMap<Key, Fitted>()

    fun fit(
        title: String,
        styles: List<TextStyle>,
        widthPx: Int,
    ): Fitted {
        if (title.isEmpty() || widthPx <= 0) return Fitted(title, 0)
        return answers.getOrPut(Key(title, styles, widthPx)) { compute(title, styles, widthPx) }
    }

    private fun compute(
        title: String,
        styles: List<TextStyle>,
        widthPx: Int,
    ): Fitted {
        val words = split(title, BreakIterator.getLineInstance(Locale.ROOT))
        for ((step, style) in styles.withIndex()) {
            if (words.all { widthOf(it.trimEnd(), style) <= widthPx }) return Fitted(breakable(words), step)
        }
        val style = styles.last()
        val firstTooWide = words.indexOfFirst { widthOf(it.trimEnd(), style) > widthPx }
        val word = words[firstTooWide].trimEnd()
        val ends = graphemeEnds(word)
        // The longest prefix that fits with the ellipsis; one grapheme at least.
        var low = 0
        var high = ends.size - 1
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (widthOf(word.substring(0, ends[middle]) + ELLIPSIS, style) <= widthPx) low = middle else high = middle - 1
        }
        return Fitted(breakable(words.take(firstTooWide) + (word.substring(0, ends[low]) + ELLIPSIS)), styles.size - 1)
    }

    /**
     * [words] joined, with a zero width space after each that does not end in a space: Android's line breaker
     * does not break after a hyphen before a letter ("Spider-Man" broke as "Spider-Ma" / "n"), the segmentation
     * does, and the space gives the layout the break the fit counted on. Nothing follows the last word.
     */
    private fun breakable(words: List<String>): String =
        buildString {
            for ((i, word) in words.withIndex()) {
                append(word)
                if (i < words.lastIndex && !word.last().isWhitespace()) append(BREAK)
            }
        }

    private fun widthOf(
        text: String,
        style: TextStyle,
    ): Int = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width

    private fun split(
        text: String,
        iterator: BreakIterator,
    ): List<String> {
        iterator.setText(text)
        val parts = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            parts += text.substring(start, end)
            start = end
            end = iterator.next()
        }
        return parts
    }

    /** The end offset of each grapheme of [word]. */
    private fun graphemeEnds(word: String): List<Int> {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(word)
        val ends = mutableListOf<Int>()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            ends += end
            end = iterator.next()
        }
        return ends.ifEmpty { listOf(word.length) }
    }

    private companion object {
        const val ELLIPSIS = "\u2026"
        const val BREAK = '\u200B'
    }
}

/** A grid's shared fitter (one text measurer and one answer cache for all its tiles); null outside a grid. */
internal val LocalTileTitleFit = compositionLocalOf<TileTitleFit?> { null }

/** The grid's fitter, or one for this tile alone where no grid gave one (a tile shown on its own). */
@Composable
internal fun currentTileTitleFit(): TileTitleFit {
    val given = LocalTileTitleFit.current
    val own = rememberTextMeasurer(cacheSize = 2)
    return given ?: remember(own) { TileTitleFit(own) }
}
