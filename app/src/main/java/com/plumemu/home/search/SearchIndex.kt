package com.plumemu.home.search

import androidx.compose.runtime.Immutable
import com.plumemu.home.EntryKind
import com.plumemu.home.GameRowModel
import com.plumemu.home.HomeContent
import java.text.Normalizer
import java.util.Locale

/** One search result: the game's row and the system it belongs to. */
@Immutable
internal data class SearchHit(
    val row: GameRowModel,
    val systemId: String,
)

/**
 * Every game of the loaded lists, ready to match: titles
 * folded once (case, accents, a few letters without a decomposition), so a keystroke only scans
 * 10,000 short strings. Recent and Favorites repeat system games and are left out. Results keep
 * the screen's order: systems in the column's order, each in its list's order, then the hidden
 * systems' games in the same way.
 */
internal class SearchIndex private constructor(
    private val hits: List<SearchHit>,
    private val folded: Array<String>,
) {
    val size: Int get() = hits.size

    /**
     * The games whose title holds every word of [query], in any order, ignoring case and accents.
     * A blank query finds nothing (the list stays empty until the person types).
     */
    fun find(query: String): List<SearchHit> {
        val words = fold(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val found = ArrayList<SearchHit>()
        for (i in folded.indices) {
            val title = folded[i]
            if (words.all { title.contains(it) }) found += hits[i]
        }
        return found
    }

    companion object {
        val Empty = SearchIndex(emptyList(), emptyArray())

        fun of(content: HomeContent): SearchIndex {
            // Hidden systems' games are found too (Settings › Library › Systems), after the column's.
            val systems = content.entries.filter { it.kind == EntryKind.System } + content.hiddenEntries
            val hits = systems.flatMap { entry -> entry.rows.map { SearchHit(it, entry.id) } }
            return SearchIndex(hits, Array(hits.size) { fold(hits[it].row.title) })
        }

        /**
         * Lower case without accents, apostrophes dropped ("yoshis" matches "Yoshi's"), other
         * punctuation turned into spaces so "Zelda: Link" matches "zelda link". Letters that do not decompose (d, o and l with a stroke, sharp s,
         * ae, oe, dotless i) are spelled out.
         */
        fun fold(text: String): String {
            val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            val out = StringBuilder(decomposed.length)
            for (c in decomposed) {
                when {
                    Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> Unit
                    // "Yoshi's" is one word: typed without the apostrophe it must still match.
                    c in APOSTROPHES -> Unit
                    c.isLetterOrDigit() -> out.append(SPELLED[c] ?: c.toString())
                    else -> if (out.isNotEmpty() && out.last() != ' ') out.append(' ')
                }
            }
            return out.toString().trimEnd()
        }

        private const val APOSTROPHES = "'\u2018\u2019\u02BC"

        private val SPELLED =
            mapOf('\u0111' to "d", '\u00F8' to "o", '\u0142' to "l", '\u00DF' to "ss", '\u00E6' to "ae", '\u0153' to "oe", '\u0131' to "i")
    }
}
