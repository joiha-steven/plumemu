package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
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
    private val cache: TileTextCache = TileTextCache.shared,
) {
    /** The answer for one title: [text] to draw in the style at [step] of the styles asked for. */
    data class Fitted(
        val text: String,
        val step: Int,
    )

    /** The answer for [title]; kept in [cache] by everything that decides it, so a grid built again finds it. */
    fun fit(
        title: String,
        styles: List<TextStyle>,
        widthPx: Int,
        density: Density,
    ): Fitted {
        if (title.isEmpty() || widthPx <= 0) return Fitted(title, 0)
        return cache.fitted(TileTextCache.FitKey(title, styles, widthPx, density.density, density.fontScale)) { compute(title, styles, widthPx) }
    }

    /** How many lines [text] takes in [style] at [widthPx], with no limit: what a tile asks before it decides to keep its year's line. */
    fun lineCount(
        text: String,
        style: TextStyle,
        widthPx: Int,
        density: Density,
    ): Int =
        if (text.isEmpty() || widthPx <= 0) {
            1
        } else {
            // Kept with the fit's key, so a tile built again (or a tile with a short title) is told at once, not measured in its first frame.
            cache.lineCount(TileTextCache.FitKey(text, listOf(style), widthPx, density.density, density.fontScale)) {
                measurer.measure(text, style, constraints = androidx.compose.ui.unit.Constraints(maxWidth = widthPx)).lineCount
            }
        }

    /** The height in px of one line of [style]: measured once per style and density for the whole process. */
    fun lineHeightPx(
        style: TextStyle,
        density: Density,
    ): Int = cache.lineHeight(TileTextCache.LineKey(style, density.density, density.fontScale)) { measurer.measure(LINE_PROBE, style, maxLines = 1).size.height }

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
        const val LINE_PROBE = "Ag"
    }
}

/**
 * What the titled tiles worked out, kept past one grid: a system's grid is composed from scratch when it is switched to (L2/R2),
 * and its tiles' title fit and line probes cost 0.46 ms a cell on the press frame. Keyed by everything
 * that decides the answer (title, styles, tile width in px, density, font scale), so a changed text size misses instead of
 * serving a stale one. Bounded: the least recently used goes first. [fitComputations] and [lineComputations] count the
 * misses, for the test.
 */
internal class TileTextCache(
    private val capacity: Int = 512,
) {
    internal data class FitKey(val title: String, val styles: List<TextStyle>, val widthPx: Int, val density: Float, val fontScale: Float)

    internal data class LineKey(val style: TextStyle, val density: Float, val fontScale: Float)

    private val fits = lru<FitKey, TileTitleFit.Fitted>()
    private val lines = lru<LineKey, Int>()
    private val lineCounts = lru<FitKey, Int>()
    var fitComputations = 0
        private set
    var lineComputations = 0
        private set
    var lineCountComputations = 0
        private set

    val size: Int get() = synchronized(this) { fits.size }

    fun fitted(
        key: FitKey,
        compute: () -> TileTitleFit.Fitted,
    ): TileTitleFit.Fitted =
        synchronized(this) { fits[key] } ?: compute().also {
            synchronized(this) {
                fitComputations++
                fits[key] = it
            }
        }

    fun lineCount(
        key: FitKey,
        compute: () -> Int,
    ): Int =
        synchronized(this) { lineCounts[key] } ?: compute().also {
            synchronized(this) {
                lineCountComputations++
                lineCounts[key] = it
            }
        }

    fun lineHeight(
        key: LineKey,
        compute: () -> Int,
    ): Int =
        synchronized(this) { lines[key] } ?: compute().also {
            synchronized(this) {
                lineComputations++
                lines[key] = it
            }
        }

    private fun <K, V> lru(): LinkedHashMap<K, V> =
        object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean = size > capacity
        }

    companion object {
        val shared = TileTextCache()
    }
}

/** A grid's shared fitter (one text measurer and one answer cache for all its tiles); null outside a grid. */
internal val LocalTileTitleFit = compositionLocalOf<TileTitleFit?> { null }

/** The grid's fitter, or one for this tile alone where no grid gave one (a tile shown on its own). */
@Composable
internal fun currentTileTitleFit(): TileTitleFit {
    val given = LocalTileTitleFit.current
    if (given != null) return given
    val own = rememberTextMeasurer(cacheSize = 2)
    return remember(own) { TileTitleFit(own) }
}
