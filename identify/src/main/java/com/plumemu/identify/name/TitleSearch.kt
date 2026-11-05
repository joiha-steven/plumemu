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

    /**
     * Up to [limit] titles close to [query] for a person to look at, never to apply: the best of
     * three readings of the name, each scored 0..1, a title kept only at or above [SUGGEST_FLOOR]
     * (fewer than [limit], or none, rather than weak ones):
     * 1. edit similarity of the keys (typos, missing letters, a roman numeral);
     * 2. edit similarity with the spaces taken out of both (run-together names, "MagicKnightRayearth2"
     *    is also split at case and digit changes first);
     * 3. shared whole words, 2 * shared / (words of the name + words of the title), so a name with a
     *    word missing finds the longer title; at least two shared words.
     * A title whose numbers differ from the name's is never offered, in either direction and roman numerals
     * counted ("Zork Qest" is not "Zork Quest II"; "Mega Man X3" is not "Mega Man 3"): both sides are read in the
     * same form, the plain key, unless the title is the name run together ("FinalFantasy3").
     * Edit similarity must also be backed by a shared word start of three letters ("mtrd fusn" and "fusion"), so
     * two short strings that merely look alike are not offered. A title that is the same name as one already
     * offered but for a trailing region word is dropped, and so is one more than [BAND] under the best.
     * When a title has the name's own key, only those are offered. Best score first, then the shorter name, then pack order; a second title with the same key
     * is dropped. A key under [SHORT_KEY] letters needs [SHORT_FLOOR]. [checkpoint] as in [find].
     */
    fun suggest(query: String, limit: Int = SUGGEST_LIMIT, checkpoint: () -> Unit = {}): List<TitleHit> {
        val words = MatchKey.tokens(query)
        if (words.isEmpty() || limit <= 0) return emptyList()
        val key = words.joinToString(" ")
        val splitKey = NameCandidates.splitWords(query)?.let { MatchKey.of(it) }?.takeIf { it != key }
        val variants = listOfNotNull(key, splitKey)
        val queryNumbers = MatchKey.numbers(key)
        val stems = variants.flatMap { it.split(' ') }.filter { it.length >= STEM && it.all(Char::isLetter) }.map { it.substring(0, STEM) }.distinct()
        val compacts = variants.map { it.replace(" ", "") }.distinct()
        val floor = if (compacts.minOf { it.length } < SHORT_KEY) SHORT_FLOOR else SUGGEST_FLOOR
        val scratch = DistanceScratch(variants.maxOf { it.length })
        val hits = ArrayList<Ranked>()
        for (i in keys.indices) {
            if (i % CHECK_EVERY == 0) checkpoint()
            val title = keys[i]
            // A different number is a different game ("Final Fantasy VI" is not "V"), unless the title is the name run together.
            val squeezed = title.replace(" ", "")
            if (MatchKey.numbers(title) != queryNumbers && squeezed !in compacts) continue
            var best = 0.0
            for (v in variants) best = maxOf(best, boundedSimilarity(v, title, floor, scratch))
            if (best < 1.0) {
                for (c in compacts) best = maxOf(best, boundedSimilarity(c, squeezed, floor, scratch))
            }
            if (best < 1.0 && stems.isNotEmpty() && stems.none { startsAWord(title, it) }) best = 0.0
            if (best < 1.0 && title.indexOf(' ') > 0) best = maxOf(best, sharedWords(words, title))
            if (best >= floor) hits += Ranked(i, 0, best, lengths[i])
        }
        hits.sortWith(ORDER)
        val seen = HashSet<String>()
        val out = ArrayList<TitleHit>(limit)
        // A title with the name's own key: nothing else is worth a look beside it.
        val hasSame = hits.firstOrNull()?.let { it.score >= 1.0 } == true
        for (h in hits) {
            if (hasSame && h.score < 1.0) break
            if (h.score < hits[0].score - BAND) break
            if (seen.add(withoutRegion(keys[h.index]))) out += TitleHit(h.index, h.score)
            if (out.size == limit) break
        }
        return out
    }

    /** [similarity] when it can reach [floor], else 0 without finishing the table. */
    private fun boundedSimilarity(query: String, title: String, floor: Double, scratch: DistanceScratch): Double {
        val longer = maxOf(query.length, title.length)
        if (longer == 0) return 0.0
        val maxDistance = ((1.0 - floor) * longer).toInt()
        if (kotlin.math.abs(query.length - title.length) > maxDistance) return 0.0
        val chars = title.toCharArray()
        val distance = scratch.distance(query, chars, 0, chars.size, maxDistance)
        return if (distance > maxDistance) 0.0 else 1.0 - distance.toDouble() / longer
    }

    private fun sharedWords(words: List<String>, title: String): Double {
        val titleWords = title.split(' ')
        val shared = words.distinct().count { it in titleWords }
        if (shared < 2) return 0.0
        val dice = 2.0 * shared / (words.size + titleWords.size)
        // Every word of the name is in the title: it only adds words ("Advance Wars 2" in "... - Black Hole Rising").
        return if (shared == words.distinct().size) maxOf(dice, 0.5 + 0.5 * shared / titleWords.size) else dice
    }

    /** [key] without trailing region words ("metroid fusion japan" is "metroid fusion"). */
    private fun withoutRegion(key: String): String {
        var end = key.length
        while (true) {
            val space = key.lastIndexOf(' ', end - 1)
            if (space <= 0 || key.substring(space + 1, end) !in REGION_WORDS) return key.substring(0, end)
            end = space
        }
    }

    private class Ranked(val index: Int, val group: Int, val score: Double, val length: Int)

    companion object {
        private const val DEFAULT_LIMIT = 100
        /** Titles scanned between two checkpoints. */
        const val CHECK_EVERY = 1024
        private const val STEM = 3
        private val REGION_WORDS = setOf("japan", "usa", "europe", "world", "asia", "korea", "australia", "brazil", "china", "germany", "france", "spain", "italy")
        /** A suggestion this far under the best one is left out. */
        private const val BAND = 0.2
        const val SUGGEST_LIMIT = 3
        const val SUGGEST_FLOOR = 0.62
        const val SHORT_FLOOR = 0.8
        const val SHORT_KEY = 6
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
