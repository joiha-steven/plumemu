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
 * 2. A name written as one word ("ZorkQuestTwo"), or with its spaces lost or "&" dropped, matches a
 *    title whose words, run together, are the same ([MatchKey.runTogether]), for it or any of its
 *    other cleanings, when every title with that form has one key; two keys give no answer
 *    (also not a fuzzy one).
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
     * exactly beats a near miss of the conservative one. [keptForms] are tried for an equal key before all of them ([ungatedForms] among them skip the region gate). [droppingSubtitle] (a dump-style name: region, no hack or other variant tag) allows the last-resort [subtitleless] reading. [regions] (pack spelling, "USA") break a
     * tie between titles with the same key.
     */
    fun match(candidates: List<String>, regions: List<String> = emptyList(), keptForms: List<String> = emptyList(), ungatedForms: Set<String> = emptySet(), droppingSubtitle: Boolean = false): NameMatch? {
        if (pack.kind != PackKind.CONSOLE || pack.titleCount == 0 || candidates.isEmpty()) return null
        val idx = index
        // The spellings that keep a version or a bracket group of the file ([TitleCleaner.keptForms])
        // are the title the file says when a pack title has them: exact keys only, and before the
        // cleaned name, which would take the title without the version or group.
        val cleanedKey = if (keptForms.isEmpty()) "" else MatchKey.of(candidates[0])
        for (form in keptForms) {
            val words = MatchKey.tokens(form)
            if (words.isEmpty()) continue
            val key = words.joinToString(" ")
            // Same key as the cleaned title: nothing kept, and its spelling must not steer the pick.
            if (key == cleanedKey) continue
            // A form ending at a region code ("Super Mario USA" from "Super.Mario.USA.EUR-GRP") may be the
            // title's own word or the region: it is taken only when the plain title is not in the pack.
            if (form !in ungatedForms && ReleaseTags.sceneRegion(words.last()) != null && exactTitles(idx, cleanedKey).isNotEmpty()) continue
            val same = exactTitles(idx, key)
            if (same.isNotEmpty()) return NameMatch(pickSpelling(same, form, regions), 1.0, isExact = true)
        }
        val tokens = candidates.map(MatchKey::tokens)
        // A run-together name that names titles of two different keys has no answer: neither a later
        // candidate's run-together form nor a fuzzy match may guess between them.
        var blocked = false
        for ((i, words) in tokens.withIndex()) {
            if (words.isEmpty()) continue
            var same = exactTitles(idx, words.joinToString(" "))
            // A name written as one word: its run-together form, before any split of it is tried.
            if (same.isEmpty() && i == 0 && words.size == 1) {
                val together = runTogetherTitles(idx, candidates[0])
                if (together == null) blocked = true else same = together
            }
            if (same.isNotEmpty()) return NameMatch(pickSpelling(same, candidates[i], regions), 1.0, isExact = true)
        }
        // Spaces lost or punctuation dropped in a name of several words ("Aqua World - Umi" as
        // "aquaworld-umi"): the title whose words run together are the same.
        // A split of a name has the same letters: its run-together form is looked up once.
        val tried = HashSet<String>()
        if (tokens[0].size == 1) tried += candidates[0].filter(Char::isLetterOrDigit).lowercase()
        for ((i, words) in tokens.withIndex()) {
            if (words.isEmpty() || !tried.add(candidates[i].filter(Char::isLetterOrDigit).lowercase())) continue
            val together = runTogetherTitles(idx, candidates[i])
            if (together == null) blocked = true else if (together.isNotEmpty()) {
                return NameMatch(pickSpelling(together, candidates[i], regions), 1.0, isExact = true)
            }
        }
        if (blocked) return null
        for ((i, words) in tokens.withIndex()) {
            if (words.isEmpty()) continue
            val found = fuzzyMatch(idx, words.joinToString(" "), words) ?: continue
            val same = exactTitles(idx, keyOf(idx, found.titleIndex))
            return found.copy(titleIndex = pickSpelling(same, candidates[i], regions))
        }
        return if (droppingSubtitle) subtitleless(idx, candidates[0], regions) else null
    }

    /**
     * Last resort: "Speedball 2 - Brutal Deluxe" for a pack that holds "Speedball 2". The part before
     * the first " - " (two words or more) must be a title's whole key, and no title of another key may
     * begin with those words ("Castlevania - Dracula X" never becomes "Castlevania" while the pack has
     * other Castlevania titles).
     */
    private fun subtitleless(idx: Index, cleanedTitle: String, regions: List<String>): NameMatch? {
        val cut = cleanedTitle.indexOf(" - ")
        if (cut <= 0) return null
        val prefix = cleanedTitle.substring(0, cut)
        // Only an edition label may be dropped: any other subtitle names another game ("Bionic Commando - Elite Forces").
        // "Game of the Year" counts only as that whole phrase; "the" and "game" alone are title words.
        val subtitle = MatchKey.words(cleanedTitle.substring(cut + 3), dropArticles = false).joinToString(" ")
            .replace("game of the year", "goty").split(' ').filter { it.isNotEmpty() }
        if (subtitle.isEmpty() || !subtitle.all { it in EDITION_LABEL_WORDS || ORDINAL.matches(it) }) return null
        val key = MatchKey.of(prefix)
        if (' ' !in key) return null
        val same = exactTitles(idx, key)
        if (same.isEmpty()) return null
        for (t in 0 until pack.titleCount) {
            val start = idx.keyStart[t]
            val end = idx.keyStart[t + 1]
            if (end - start <= key.length || idx.keyChars[start + key.length] != ' ') continue
            if (key.indices.all { idx.keyChars[start + it] == key[it] }) return null
        }
        return NameMatch(pickSpelling(same, prefix, regions), SUBTITLELESS_SCORE, isExact = false)
    }

    /**
     * The titles whose run-together form is one of [name]'s ([MatchKey.runTogether]): empty when
     * none, null when they differ in key (no answer). Titles of one key are one game.
     */
    private fun runTogetherTitles(idx: Index, name: String): IntArray? {
        var found = IntArray(0)
        for (form in MatchKey.runTogether(name)) {
            for (t in runTogetherForm(idx, form)) if (t !in found) found += t
        }
        // Digits the name keeps apart stay apart ("20-02" is not "2002"); digits the name ran together
        // may be the title's separate numbers ("Ran21999" for "Ran2 1999").
        if (found.isNotEmpty()) {
            val runs = MatchKey.digitRuns(name)
            found = found.filter { MatchKey.sameDigits(runs, MatchKey.digitRuns(pack.titleName(it))) }.toIntArray()
        }
        if (found.size > 1 && (1 until found.size).any { !sameKey(idx, found[0], found[it]) }) {
            // Titles of different keys: only a spelling that is one title exactly, spaces aside (case
            // and punctuation included), tells them apart ("SpaceHarrier3D" for "Space Harrier 3D", not
            // "Space Harrier 3-D"); a lower-case or punctuation-free name does not.
            val bare = name.replace(" ", "")
            val same = found.filter { pack.titleName(it).replace(" ", "") == bare }
            // Only for a name with no spaces: "Dragon Warrior I II" is not "Dragon Warrior III".
            return if (' ' !in name && same.size == 1) same.toIntArray() else null
        }
        found.sort()
        return found
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
            val raw = MatchKey.words(name, dropArticles = false)
            val words = MatchKey.withoutArticles(raw).map(MatchKey::normaliseWord)
            val key = words.joinToString(" ")
            for (form in MatchKey.runTogether(name, raw)) {
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

    /** Every title with the run-together form [form], in index order. */
    private fun runTogetherForm(idx: Index, form: String): IntArray =
        titlesWithHash(idx.compact, form.hashCode()) { form in MatchKey.runTogether(pack.titleName(it)) }

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
        private const val SUBTITLELESS_SCORE = 0.9
        private val ORDINAL = Regex("""\d+(?:st|nd|rd|th)""")

        /** Words of an edition label, as [MatchKey] writes them (apostrophes dropped). */
        private val EDITION_LABEL_WORDS = setOf(
            "edition", "anniversary", "deluxe", "special", "remastered", "remaster", "collectors", "directors", "cut",
            "limited", "complete", "definitive", "ultimate", "gold", "platinum", "greatest", "hits", "goty",
        )
        private const val AVERAGE_KEY = 24
    }
}
