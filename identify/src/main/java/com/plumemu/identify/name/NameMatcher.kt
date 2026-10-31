package com.plumemu.identify.name

import com.plumemu.identify.pack.NamePack
import com.plumemu.identify.pack.PackKind
import com.plumemu.identify.pack.PackSource

/** A title of the pack that a name matched. [score] is 1.0 for an exact key match. */
data class NameMatch(val titleIndex: Int, val score: Double, val isExact: Boolean)

/**
 * Finds the title of a console pack that a cleaned file name means (layer 4 of identification).
 *
 * 1. Equal [MatchKey]s match ("Zork Quest II" and "the zork quest 2").
 * 2. A name written as one word ("ZorkQuestTwo") matches a title whose words, run together, are
 *    the same ([MatchKey.compact]), for it or any of its other cleanings: a camel split cannot
 *    find the small words such names run in ("PantsandFriends").
 * 3. Otherwise the titles sharing the rarest words of the name are scored with the
 *    Damerau-Levenshtein similarity of the keys (optimal string alignment: insert, delete,
 *    substitute, swap two neighbours; similarity = 1 - distance / longer length). The best wins
 *    when it reaches [threshold], beats every other title by [margin], both names hold the
 *    same numbers, so a sequel never takes its predecessor's name ("Zork Quest" never matches
 *    "Zork Quest 2"), and they do not differ by an edition word or a whole short word
 *    ([differsByEdition]: "Zork GBC" never matches "Zork GB").
 *
 * Several titles can share a key ("Zork Quest II" and "Zork Quest 2", "KAO the Kangaroo" and
 * "Kao the Kangaroo"). The one spelled exactly like the name (case included) wins; then the one
 * closest to it by case-sensitive edit distance; then one with a release in the name's regions;
 * then the one from the higher-ranked source (`SRCS` order, which the pack tool writes in rank
 * order); then the first.
 *
 * The index is built on the first [match] and kept while this matcher lives: the keys in one
 * character array, an exact-key table, a run-together key table and a word index of hashed words,
 * all in primitive arrays (see [indexBytes]). Thread-safe after construction; [match] allocates
 * only small per-call arrays.
 *
 * @param threshold 0.85: one typo in a 7-letter name passes (6/7 = 0.857), two typos need about
 *   14 letters; tests in `NameMatcherTest` hold the line.
 */
class NameMatcher(
    private val pack: NamePack,
    private val threshold: Double = DEFAULT_THRESHOLD,
    private val margin: Double = DEFAULT_MARGIN,
) {
    private class Index(
        val keyChars: CharArray,
        val keyStart: IntArray,
        /** (key hash shl 32) or title index, sorted. */
        val exact: LongArray,
        /** (run-together key hash shl 32) or title index, sorted. */
        val compact: LongArray,
        val wordHashes: IntArray,
        val postingStart: IntArray,
        val postings: IntArray,
    )

    private val index: Index by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { build() }

    /** The best title for [cleanedTitle], or null when none is close enough or two are equally close. */
    fun match(cleanedTitle: String): NameMatch? = match(listOf(cleanedTitle))

    /**
     * The best title for the first of [candidates] (conservative cleanings first, see
     * `NameCandidates`) that matches: every candidate is tried for an equal key (then a
     * run-together one) before any is scored fuzzily, so an aggressive cleaning that names a game
     * exactly beats a near miss of the conservative one. [regions] (pack spelling, "USA") break a
     * tie between titles with the same key.
     */
    fun match(candidates: List<String>, regions: List<String> = emptyList()): NameMatch? {
        if (pack.kind != PackKind.CONSOLE || pack.titleCount == 0 || candidates.isEmpty()) return null
        val idx = index
        val tokens = candidates.map(MatchKey::tokens)
        for ((i, words) in tokens.withIndex()) {
            if (words.isEmpty()) continue
            var same = exactTitles(idx, words.joinToString(" "))
            // A name written as one word: its run-together form, before any split of it is tried.
            if (same.isEmpty() && i == 0 && words.size == 1) same = compactTitles(idx, MatchKey.compact(candidates[0]))
            if (same.isNotEmpty()) return NameMatch(pickSpelling(same, candidates[i], regions), 1.0, isExact = true)
        }
        for ((i, words) in tokens.withIndex()) {
            if (words.isEmpty()) continue
            val found = fuzzyMatch(idx, words.joinToString(" "), words) ?: continue
            val same = exactTitles(idx, keyOf(idx, found.titleIndex))
            return found.copy(titleIndex = pickSpelling(same, candidates[i], regions))
        }
        return null
    }

    /** Bytes the index holds in its arrays (object headers left out); built on first call. */
    fun indexBytes(): Long {
        val idx = index
        return idx.keyChars.size * 2L + idx.keyStart.size * 4L + idx.exact.size * 8L + idx.compact.size * 8L +
            idx.wordHashes.size * 4L + idx.postingStart.size * 4L + idx.postings.size * 4L
    }

    private fun build(): Index {
        val count = pack.titleCount
        val chars = StringBuilder(count * AVERAGE_KEY)
        val keyStart = IntArray(count + 1)
        val exact = LongArray(count)
        var compact = LongArray(count)
        var compactCount = 0
        var pairs = LongArray(count * 4)
        var pairCount = 0
        for (t in 0 until count) {
            val name = pack.titleName(t)
            val raw = MatchKey.words(name)
            val words = raw.map(MatchKey::normaliseWord)
            val key = words.joinToString(" ")
            val forms = if ('&' in name) MatchKey.compactForms(name) else listOf(raw.joinToString(""))
            for (form in forms) {
                if (compactCount == compact.size) compact = compact.copyOf(compact.size * 2 + 1)
                compact[compactCount++] = (form.hashCode().toLong() shl 32) or t.toLong()
            }
            keyStart[t] = chars.length
            chars.append(key)
            exact[t] = (key.hashCode().toLong() shl 32) or t.toLong()
            for (word in words.distinct()) {
                if (pairCount == pairs.size) pairs = pairs.copyOf(pairs.size * 2)
                pairs[pairCount++] = (word.hashCode().toLong() shl 32) or t.toLong()
            }
        }
        keyStart[count] = chars.length
        exact.sort()
        compact = compact.copyOf(compactCount)
        compact.sort()
        pairs = pairs.copyOf(pairCount)
        pairs.sort()
        val distinctWords = countDistinctHashes(pairs)
        val wordHashes = IntArray(distinctWords)
        val postingStart = IntArray(distinctWords + 1)
        val postings = IntArray(pairCount)
        var w = -1
        for (i in 0 until pairCount) {
            val hash = (pairs[i] shr 32).toInt()
            if (w < 0 || wordHashes[w] != hash) {
                w++
                wordHashes[w] = hash
                postingStart[w] = i
            }
            postings[i] = pairs[i].toInt()
        }
        postingStart[distinctWords] = pairCount
        return Index(chars.toString().toCharArray(), keyStart, exact, compact, wordHashes, postingStart, postings)
    }

    private fun countDistinctHashes(sortedPairs: LongArray): Int {
        var distinct = 0
        for (i in sortedPairs.indices) {
            if (i == 0 || (sortedPairs[i] shr 32) != (sortedPairs[i - 1] shr 32)) distinct++
        }
        return distinct
    }

    /** Every title whose key is [key], in index order. */
    private fun exactTitles(idx: Index, key: String): IntArray =
        titlesWithHash(idx.exact, key.hashCode()) { keyEquals(idx, it, key) }

    /** Every title whose run-together key is [compact], in index order; empty for an empty one. */
    private fun compactTitles(idx: Index, compact: String): IntArray {
        if (compact.isEmpty()) return IntArray(0)
        return titlesWithHash(idx.compact, compact.hashCode()) { compact in MatchKey.compactForms(pack.titleName(it)) }
    }

    private inline fun titlesWithHash(table: LongArray, hashCode: Int, equal: (Int) -> Boolean): IntArray {
        val hash = hashCode.toLong()
        var low = 0
        var high = table.size
        val target = hash shl 32
        while (low < high) {
            val mid = (low + high) ushr 1
            if (table[mid] < target) low = mid + 1 else high = mid
        }
        var found = IntArray(0)
        var i = low
        while (i < table.size && (table[i] shr 32) == hash) {
            val title = table[i].toInt()
            if (equal(title) && title !in found) found += title
            i++
        }
        found.sort()
        return found
    }

    /** The title of [titles] (same key) spelled most like [reference]; see the class KDoc for the order. */
    private fun pickSpelling(titles: IntArray, reference: String, regions: List<String>): Int {
        if (titles.size == 1) return titles[0]
        val names = titles.map(pack::titleName)
        names.indexOf(reference).takeIf { it >= 0 }?.let { return titles[it] }
        // A name with no punctuation (scene, dotted, run-together) says nothing about it: compare
        // the titles without theirs, so a colon is not preferred over a dash by chance.
        val bare = reference.none(::isPunctuation)
        val spellings = if (bare) names.map(::withoutPunctuation) else names
        val scratch = DistanceScratch(reference.length)
        val distances = spellings.map { scratch.distance(reference, it.toCharArray(), 0, it.length, Int.MAX_VALUE - 1) }
        val closest = distances.min()
        val tied = titles.indices.filter { distances[it] == closest }
        if (tied.size == 1) return titles[tied[0]]
        val ranks = sourceRanks
        return tied.minWith(
            compareBy<Int> { i ->
                if (pack.releasesOf(titles[i]).any { r -> r.regions.any { region -> regions.any { it.equals(region, ignoreCase = true) } } }) 0 else 1
            }.thenBy { i -> pack.releasesOf(titles[i]).minOfOrNull { r -> r.source?.let { ranks[it] } ?: Int.MAX_VALUE } ?: Int.MAX_VALUE }
                .thenBy { i -> titles[i] },
        ).let { titles[it] }
    }

    private fun isPunctuation(c: Char) = !c.isLetterOrDigit() && !c.isWhitespace()

    private fun withoutPunctuation(name: String): String =
        name.map { if (isPunctuation(it)) ' ' else it }.joinToString("").split(' ').filter { it.isNotEmpty() }.joinToString(" ")

    /** Rank of each source of the pack: its place in `SRCS`. */
    private val sourceRanks: Map<PackSource, Int> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        pack.sources().withIndex().associate { (i, source) -> source to i }
    }

    private fun keyOf(idx: Index, title: Int): String =
        String(idx.keyChars, idx.keyStart[title], idx.keyStart[title + 1] - idx.keyStart[title])

    private fun fuzzyMatch(idx: Index, key: String, words: List<String>): NameMatch? {
        val candidates = candidates(idx, words.distinct())
        if (candidates.isEmpty()) return null
        val queryNumbers = MatchKey.numbers(key)
        val floor = threshold - margin
        val scratch = DistanceScratch(key.length)
        val keys = CharSequenceView(idx.keyChars)
        var best = -1
        var bestScore = 0.0
        var second = 0.0
        for (title in candidates) {
            val start = idx.keyStart[title]
            val end = idx.keyStart[title + 1]
            val longer = maxOf(key.length, end - start)
            val maxDistance = ((1.0 - floor) * longer).toInt()
            if (kotlin.math.abs(key.length - (end - start)) > maxDistance) continue
            if (MatchKey.numbers(keys, start, end) != queryNumbers) continue
            val distance = scratch.distance(key, idx.keyChars, start, end, maxDistance)
            if (distance > maxDistance) continue
            val score = 1.0 - distance.toDouble() / longer
            when {
                best >= 0 && sameKey(idx, best, title) -> Unit
                score > bestScore -> {
                    second = bestScore
                    best = title
                    bestScore = score
                }
                score > second -> second = score
            }
        }
        if (best < 0 || bestScore < threshold || bestScore - second < margin) return null
        if (differsByEdition(words, keyOf(idx, best).split(' '))) return null
        return NameMatch(best, bestScore, isExact = false)
    }

    /** Titles sharing the rarest words of the name, at most [MAX_CANDIDATES] (rarest words first). */
    private fun candidates(idx: Index, words: List<String>): IntArray {
        val ranges = words.mapNotNull { word ->
            val w = idx.wordHashes.binarySearch(word.hashCode())
            if (w < 0) null else idx.postingStart[w] until idx.postingStart[w + 1]
        }.sortedBy { it.last - it.first }
        val seen = HashSet<Int>()
        for (range in ranges) {
            if (seen.isNotEmpty() && seen.size + (range.last - range.first + 1) > MAX_CANDIDATES) break
            for (p in range) {
                seen += idx.postings[p]
                if (seen.size >= MAX_CANDIDATES) break
            }
            if (seen.size >= MAX_CANDIDATES) break
        }
        return seen.toIntArray().also { it.sort() }
    }

    private fun keyEquals(idx: Index, title: Int, key: String): Boolean {
        val start = idx.keyStart[title]
        if (idx.keyStart[title + 1] - start != key.length) return false
        for (i in key.indices) if (idx.keyChars[start + i] != key[i]) return false
        return true
    }

    private fun sameKey(idx: Index, a: Int, b: Int): Boolean {
        val length = idx.keyStart[a + 1] - idx.keyStart[a]
        if (idx.keyStart[b + 1] - idx.keyStart[b] != length) return false
        for (i in 0 until length) if (idx.keyChars[idx.keyStart[a] + i] != idx.keyChars[idx.keyStart[b] + i]) return false
        return true
    }

    /**
     * True when two keys of a near match differ by a word that makes them different games: one
     * name has a whole extra word of up to [SHORT_WORD] letters (letters only: "CD1" is a disc
     * tag the cleaner does not read yet, not another game; not a [FILLER_WORDS] word) or an
     * [EDITION_WORDS] word
     * ("Zork Vol II" / "Zork II", "Zork Plus" / "Zork"), or the same place holds two edition
     * words, or an edition word and a word it starts or ends ("Zork GBC" / "Zork GB"); a typo of
     * an edition word ("Plsu") is still a typo.
     */
    internal fun differsByEdition(query: List<String>, title: List<String>): Boolean {
        val (longer, shorter) = if (query.size >= title.size) query to title else title to query
        when (longer.size - shorter.size) {
            0 -> {
                return query.indices.any { i -> query[i] != title[i] && otherEdition(query[i], title[i]) }
            }
            1 -> {
                val extra = longer.indices.firstOrNull { i -> longer.subList(0, i) + longer.subList(i + 1, longer.size) == shorter }
                    ?: return false
                val word = longer[extra]
                return (word.length <= SHORT_WORD && word.all { it.isLetter() } && word !in FILLER_WORDS) || word in EDITION_WORDS
            }
            else -> return false
        }
    }

    private fun otherEdition(a: String, b: String): Boolean {
        val aEdition = a in EDITION_WORDS
        val bEdition = b in EDITION_WORDS
        return (aEdition && bEdition) || ((aEdition || bEdition) && (a.startsWith(b) || b.startsWith(a)))
    }

    private class CharSequenceView(private val chars: CharArray) : CharSequence {
        override val length: Int get() = chars.size
        override fun get(index: Int): Char = chars[index]
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = String(chars, startIndex, endIndex - startIndex)
        override fun toString(): String = String(chars)
    }

    companion object {
        const val DEFAULT_THRESHOLD = 0.85
        const val DEFAULT_MARGIN = 0.05

        /** A whole extra word this short is never a typo ("Vol", "Hen", "GB"). */
        private const val SHORT_WORD = 3

        /** Short words names written without punctuation drop or add ("Pachi Slot" for "Pachi & Slot"). */
        private val FILLER_WORDS = setOf("and", "the", "of", "a", "an")

        /**
         * Words that name an edition or a system, in key form: a title that differs from the name
         * only there is another release ("Zork GB" and "Zork GBC", "Zork" and "Zork Deluxe").
         */
        internal val EDITION_WORDS = setOf(
            "gb", "gbc", "gba", "ds", "dsi", "3ds", "3d", "dx", "ex", "sp", "jr", "plus", "deluxe",
            "advance", "color", "turbo", "special",
        )

        /** Titles scored per lookup at most: keeps a name made of common words under a millisecond. */
        internal const val MAX_CANDIDATES = 512
        private const val AVERAGE_KEY = 24
    }
}
