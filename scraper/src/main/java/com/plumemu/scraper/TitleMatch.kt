package com.plumemu.scraper

import java.text.Normalizer
import java.util.Locale

/**
 * Whether a name search result is the requested game. `jeuRecherche.php` returns up to 30 loosely
 * related games in no useful order ("Sonic The Hedgehog 2" before "Sonic The Hedgehog"), so a
 * result counts only when one of its names equals the request's title after [normalise].
 */
internal object TitleMatch {
    private val MARKS = Regex("\\p{Mn}+")
    private val BRACKETED = Regex("""\([^)]*\)|\[[^\]]*]""")
    private val TRAILING_THE = Regex(""",\s*the$""")
    private val LEADING_THE = Regex("""^the\s+""")
    private val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}]+""")

    /**
     * Lowercase, accents stripped, `(...)` and `[...]` parts dropped, a leading "The " or trailing
     * ", The" dropped (so "Legend of Zelda, The" equals "The Legend of Zelda"), every run of other
     * characters one space, trimmed.
     */
    fun normalise(title: String): String {
        val folded = Normalizer.normalize(title.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(MARKS, "")
        val bare = folded.replace(BRACKETED, " ").trim().replace(TRAILING_THE, "").replace(LEADING_THE, "")
        return bare.replace(NON_ALPHANUMERIC, " ").trim()
    }

    /**
     * True when one of [names] equals [title] after lowercasing and stripping accents and nothing
     * else: brackets, punctuation and "The" stay. The tie-break between several games that
     * [matches] accepts ("Limbo" and "Limbo (Special Edition)").
     */
    fun exactName(title: String, names: List<String?>): Boolean {
        fun fold(text: String) = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(MARKS, "").trim()
        val wanted = fold(title)
        return wanted.isNotEmpty() && names.any { it != null && fold(it) == wanted }
    }

    private val OTHER_RELEASE = Regex("""[(\[]\s*(1[89]|20)\d\d\s*[)\]]|(\(|\[|\s-\s)[^)\]]*\b(remake|remastered|reboot)\b""", RegexOption.IGNORE_CASE)

    /**
     * True when one of [names] marks a different release of a game, not an edition of it: a year in
     * brackets ("Prey (2017)") or the word remake, remastered or reboot in brackets or after " - ".
     * Keeps the exact-name tie-break from picking the 2006 "Prey" over the 2017 one.
     */
    fun looksLikeOtherRelease(names: List<String?>): Boolean = names.any { it != null && OTHER_RELEASE.containsMatchIn(it) }

    private val SUBTITLE_SEPARATOR = Regex(""":|\s[-–]\s""")

    /**
     * Extra search texts for `jeuRecherche.php`, whose search chokes on punctuation (a colon gave an
     * empty entry for "Captain Toad: Treasure Tracker"): [title] with punctuation folded to spaces,
     * then the part before the first subtitle separator when it has two words or more. Texts equal to
     * [title] or to each other are left out. The answers are still judged against the whole title by
     * [matches]; this only changes what is asked.
     */
    fun searchVariants(title: String): List<String> {
        val asked = title.trim()
        fun fold(text: String) = text.replace(BRACKETED, " ").replace(NON_ALPHANUMERIC, " ").trim()
        val variants = mutableListOf<String>()
        val folded = fold(asked)
        if (folded.isNotEmpty() && folded != asked) variants += folded
        val head = asked.split(SUBTITLE_SEPARATOR, limit = 2).first()
        if (head != asked) {
            val headFolded = fold(head)
            if (headFolded.split(' ').size >= 2 && headFolded != asked && headFolded !in variants) variants += headFolded
        }
        return variants
    }

    /**
     * The parent game's title of a hack: [title] before its first subtitle separator (" - " or ":"), brackets removed,
     * spaces evened out. Null when there is no separator or nothing is left ("Crash Bandicoot - N. Sane Trilogy" gives
     * "Crash Bandicoot").
     */
    fun parentTitle(title: String): String? {
        val parts = title.split(SUBTITLE_SEPARATOR, limit = 2)
        if (parts.size < 2) return null
        return parts.first().replace(BRACKETED, " ").split(' ').filter { it.isNotEmpty() }.joinToString(" ").takeIf { it.isNotEmpty() }
    }

    /**
     * True when [title] has at least [MIN_CONTAINED_WORDS] words and one of [names], after [normalise],
     * holds all of them as a run of whole words ("999 nine hours nine persons nine doors" holds
     * "nine hours nine persons nine doors"). Used only for the one game a variant search answers.
     */
    fun containedIn(title: String, names: List<String?>): Boolean {
        val wanted = normalise(title)
        if (wanted.split(' ').size < MIN_CONTAINED_WORDS) return false
        return names.any { it != null && " ${normalise(it)} ".contains(" $wanted ") }
    }

    private const val MIN_CONTAINED_WORDS = 3

    /** True when one of [names] equals [title] after [normalise]; an empty title matches nothing. */
    fun matches(title: String, names: List<String?>): Boolean {
        val wanted = normalise(title)
        if (wanted.isEmpty()) return false
        return names.any { it != null && normalise(it) == wanted }
    }
}
