package com.plumemu.identify.name

import com.plumemu.identify.arcade.ArcadeSystems
import java.util.Locale

/**
 * Title case for a cleaned file name that is all lower case or ALL UPPER CASE ("dynamite cop!",
 * "PAC-MAN"), so the list never shows a shouting or flat name when no pack title replaces it.
 *
 * - A title with both cases is left as it is ("SpongeBob", "KAO the Kangaroo"); so is one with no
 *   cased letters (digits) and one with any letter that has no case (kana, kanji, hangul).
 * - Each word gets a capital first letter, and so does each letter after a hyphen or dot inside it
 *   ("Pac-Man", "M.A.S.H"); an apostrophe does not start a new word ("Don't").
 * - [SMALL_WORDS] stay lower case except as the first word or after a subtitle dash or colon.
 * - [ACRONYMS], roman numerals and a number with K ("2K", "2K10") are upper case; in an ALL-UPPER
 *   title so is a word with no vowel ("XXX", "BMX"; not the [ABBREVIATIONS] "Dr", "Mr", "St", "Jr").
 * - A word mixing letters and digits that is not an acronym ("xmen2p", "sf2") is a code: kept as is.
 *
 * Used only for the fallback a person sees ([forSystem]); [TitleCleaner] keeps the file's case.
 */
object TitleCase {
    /**
     * The title shown for a file of [systemId] when no pack names it: [readable], except for arcade
     * systems, whose file names are short names ("mslug") that stay as written.
     */
    fun forSystem(title: String, systemId: String): String =
        if (ArcadeSystems.isArcade(systemId)) title else readable(stripListNumber(title))

    private val listNumber = Regex("""^(?!007\b)(?:\d{1,2}|0\d\d)(?:\s+-\s+|\.\s+|_\s+)(?=\S)""")

    /**
     * A collection's running number ("01 - ", "1. ", "001_ ") is not part of the name a person
     * sees. Three digits without a leading zero ("300 - March to Glory") and "007" are real titles
     * (the corpus has five), so only [NameCandidates] tries those without the number. The title is kept as is when [NameCandidates] could still match it in a database: this
     * runs only on the fallback, after the matcher found nothing.
     */
    private fun stripListNumber(title: String): String = title.replaceFirst(listNumber, "").ifBlank { title }

    private val SMALL_WORDS = setOf("a", "an", "the", "of", "and", "in", "on", "at", "to", "for", "vs", "no", "wo", "ga", "de")

    private val ACRONYMS = setOf(
        "fifa", "uefa", "nba", "nfl", "nhl", "mlb", "nascar", "ncaa", "pga", "wwe", "wwf", "wcw", "ecw", "ufc",
        "espn", "tmnt", "gta", "bmx", "atv", "wrc", "f1", "snk", "usa", "uk", "cd", "dx", "ex", "hd", "gb", "gbc",
        "gba", "3d", "rpg", "nes", "snes", "psp", "ds", "tv",
    )

    /** Abbreviations without a vowel that are written with one capital ("Dr. Mario", "St. Louis"). */
    private val ABBREVIATIONS = setOf("mr", "mrs", "dr", "st", "jr", "sr", "pt", "ft", "vs")

    private val numberWithK = Regex("""\d+k\d*""")
    private val VOWELS = "aeiouy"

    private val ROMAN = setOf(
        "i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x", "xi", "xii", "xiii", "xiv", "xv",
        "xvi", "xvii", "xviii", "xix", "xx",
    )

    /** [title] in title case when it is all one case; otherwise unchanged. */
    fun readable(title: String): String {
        var lower = false
        var upper = false
        for (c in title) {
            when {
                Character.isLowerCase(c) -> lower = true
                Character.isUpperCase(c) -> upper = true
                // Kana, kanji, hangul: a letter without case. Such a title is kept as written.
                Character.isLetter(c) -> return title
            }
        }
        if (lower == upper) return title
        val shouting = upper
        val words = title.split(' ')
        val out = StringBuilder(title.length)
        var subtitleStart = true
        for ((i, word) in words.withIndex()) {
            if (i > 0) out.append(' ')
            out.append(recase(word, subtitleStart, shouting))
            subtitleStart = word == "-" || word.endsWith(":")
        }
        return out.toString()
    }

    private fun recase(word: String, first: Boolean, shouting: Boolean): String {
        val core = word.trim { !it.isLetterOrDigit() }.lowercase(Locale.ROOT).trimEnd('.')
        if (core.isEmpty()) return word
        val plain = core.filter { it.isLetterOrDigit() }
        return when {
            core in ACRONYMS || plain in ROMAN || numberWithK.matches(plain) -> word.uppercase(Locale.ROOT)
            shouting && plain.length >= 2 && plain.all { it in 'a'..'z' } && plain.none { it in VOWELS } && plain !in ABBREVIATIONS ->
                word.uppercase(Locale.ROOT)
            !first && plain in SMALL_WORDS -> word.lowercase(Locale.ROOT)
            plain.any { it.isDigit() } && plain.any { it.isLetter() } -> word
            else -> capitalise(word.lowercase(Locale.ROOT))
        }
    }

    private fun capitalise(word: String): String {
        val out = StringBuilder(word.length)
        var startOfPart = true
        for (c in word) {
            out.append(if (startOfPart && c.isLetter()) c.titlecaseChar() else c)
            startOfPart = when {
                c.isLetterOrDigit() -> false
                c in APOSTROPHES -> false
                else -> true
            }
        }
        return out.toString()
    }

    private val APOSTROPHES = setOf('\'', '\u2019', '`')
}
