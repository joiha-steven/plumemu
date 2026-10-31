package com.plumemu.identify.name

/** One title [TitleSearch] found: [index] into the names it was built from. */
data class TitleHit(val index: Int, val score: Double)

/**
 * Fix name…: ranks a system's titles for what the person types, in the same
 * [MatchKey] form the name matcher compares in, so "zork quest 2" finds "Zork Quest II".
 *
 * Best first:
 * 1. the same key;
 * 2. the key starts with what was typed (the last word may be half typed);
 * 3. every typed word starts a word of the title, in any order;
 * 4. the name matcher's Damerau-Levenshtein similarity of the keys (at least [FUZZY_FLOOR]),
 *    tried only on titles where a typed word's first three letters start a word, so a typo
 *    later in a word is forgiven without scoring every title.
 *
 * Inside a group: higher similarity, then the shorter name, then pack order. The keys are made
 * once, in the constructor (call it off the main thread: tens of thousands of titles for
 * arcade); [find] scans them all, allocating only for hits. Thread-safe after construction.
 */
class TitleSearch(names: List<String>) {
    private val keys: Array<String> = Array(names.size) { MatchKey.of(names[it]) }
    private val lengths: IntArray = IntArray(names.size) { names[it].length }

    val size: Int get() = keys.size

    /**
     * At most [limit] titles for [query], best first; empty when the query has no words.
     * [checkpoint] is called every [CHECK_EVERY] titles and may throw (a coroutine's
     * `ensureActive()`), so a search a newer key press made stale stops part-way.
     */
    fun find(query: String, limit: Int = DEFAULT_LIMIT, checkpoint: () -> Unit = {}): List<TitleHit> {
        val words = MatchKey.tokens(query)
        if (words.isEmpty() || limit <= 0) return emptyList()
        val key = words.joinToString(" ")
        val stems = words.filter { it.length >= STEM }.map { it.substring(0, STEM) }.distinct()
        val scratch = DistanceScratch(key.length)
        val hits = ArrayList<Ranked>()
        for (i in keys.indices) {
            if (i % CHECK_EVERY == 0) checkpoint()
            val title = keys[i]
            val group = when {
                title == key -> GROUP_SAME
                title.startsWith(key) -> GROUP_STARTS
                words.all { startsAWord(title, it) } -> GROUP_WORDS
                stems.any { startsAWord(title, it) } -> GROUP_FUZZY
                else -> continue
            }
            val score = similarity(key, title, scratch)
            if (group == GROUP_FUZZY && score < FUZZY_FLOOR) continue
            hits += Ranked(i, group, score, lengths[i])
        }
        hits.sortWith(ORDER)
        return hits.asSequence().take(limit).map { TitleHit(it.index, it.score) }.toList()
    }

    private class Ranked(val index: Int, val group: Int, val score: Double, val length: Int)

    companion object {
        private const val DEFAULT_LIMIT = 100
        /** Titles scanned between two checkpoints. */
        const val CHECK_EVERY = 1024
        private const val STEM = 3
        private const val FUZZY_FLOOR = 0.6
        private const val GROUP_SAME = 0
        private const val GROUP_STARTS = 1
        private const val GROUP_WORDS = 2
        private const val GROUP_FUZZY = 3

        private val ORDER: Comparator<Ranked> =
            compareBy<Ranked> { it.group }.thenByDescending { it.score }.thenBy { it.length }.thenBy { it.index }

        /** Whether [word] starts one of the space-separated words of [key]. */
        private fun startsAWord(key: String, word: String): Boolean {
            var from = 0
            while (from <= key.length - word.length) {
                if (key.startsWith(word, from)) return true
                val space = key.indexOf(' ', from)
                if (space < 0) return false
                from = space + 1
            }
            return false
        }

        private fun similarity(query: String, title: String, scratch: DistanceScratch): Double {
            val longer = maxOf(query.length, title.length)
            if (longer == 0) return 1.0
            val chars = title.toCharArray()
            val distance = scratch.distance(query, chars, 0, chars.size, longer)
            return 1.0 - distance.toDouble() / longer
        }
    }
}
