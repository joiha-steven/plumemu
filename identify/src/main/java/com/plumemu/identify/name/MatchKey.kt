package com.plumemu.identify.name

import java.text.Normalizer
import java.util.Locale

/**
 * The form two names are compared in. The input is a title `library`'s `TitleCleaner` already
 * cleaned (tags, regions, underscores, "Title, The" handled there); this only evens out spelling:
 *
 * - lower case; accents removed (NFD, combining marks dropped); a few letters NFD does not split
 *   are spelled out (sharp s, ae, oe, o and d with a stroke, thorn);
 * - "&" read as "and"; apostrophes dropped ("Zork's" and "Zorks" meet); every other character that
 *   is not a letter or digit is a space, so "-" and ":" subtitle separators disappear;
 * - numbers lose leading zeros; roman numerals II to XX of two letters or more become digits.
 *   Single "V" and "X" stay letters: they are names as often as numbers ("Zork X" is not
 *   "Zork 10");
 * - a leading "the", "a" or "an" and a trailing "the" are dropped when other words remain. A
 *   trailing "a" stays: it is more often a variant letter ("Disk A") than a moved article.
 *
 * [compact] is the same without the number rules, words run together, for names written as one
 * word.
 */
object MatchKey {
    /** The key of [title]: its words, normalised, joined by single spaces; empty when none remain. */
    fun of(title: String): String = tokens(title).joinToString(" ")

    /** The words of the key of [title]. */
    fun tokens(title: String): List<String> = words(title).map(::normaliseWord)

    /**
     * The words of [title] run together, as in its key but with numbers and numerals as written
     * ("Final Fantasy III" -> "finalfantasyiii", "The Lodoss-tou Senki" -> "lodosstousenki"): what a
     * name written without spaces is compared with (`NameMatcher`).
     */
    fun compact(title: String): String = words(title).joinToString("")

    /**
     * Every run-together form of [title]: [compact], and for a title with "&" also the form
     * without it, since names written as one word drop it ("Tom & Zork" -> "TomZork").
     */
    fun compactForms(title: String): List<String> {
        val first = compact(title)
        if ('&' !in title) return listOf(first)
        return listOf(first, words(title, ampersand = " ").joinToString("")).distinct()
    }

    /**
     * The words of [title] as [tokens] gives them but before the number rules ([normaliseWord]);
     * the articles are dropped here, which the number rules cannot change.
     */
    internal fun words(title: String, ampersand: String = " and "): List<String> {
        val folded = StringBuilder(title.length)
        for (c in Normalizer.normalize(title, Normalizer.Form.NFKC)) folded.append(SPELLED_OUT[c] ?: c)
        val decomposed = Normalizer.normalize(folded, Normalizer.Form.NFD).lowercase(Locale.ROOT)
        val plain = StringBuilder(decomposed.length + 8)
        for (c in decomposed) {
            when {
                Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> Unit
                c in APOSTROPHES -> Unit
                c == '&' -> plain.append(ampersand)
                Character.isLetterOrDigit(c) -> plain.append(c)
                else -> plain.append(' ')
            }
        }
        val words = plain.split(' ').filter { it.isNotEmpty() }.toMutableList()
        if (words.size >= 2 && words[0] in LEADING_ARTICLES) words.removeAt(0)
        if (words.size >= 2 && words.last() == "the") words.removeAt(words.size - 1)
        return words
    }

    /** The numbers in [key], in order: two keys must have the same ones to match. */
    internal fun numbers(key: CharSequence, from: Int = 0, to: Int = key.length): List<String> {
        val out = ArrayList<String>(2)
        var i = from
        while (i < to) {
            if (key[i].isDigit() && (i == from || key[i - 1] == ' ')) {
                var j = i
                while (j < to && key[j].isDigit()) j++
                if (j == to || key[j] == ' ') out += key.substring(i, j)
                i = j
            } else {
                i++
            }
        }
        return out
    }

    internal fun normaliseWord(word: String): String {
        if (word.all { it in '0'..'9' }) return word.trimStart('0').ifEmpty { "0" }
        return ROMAN[word] ?: word
    }

    private val LEADING_ARTICLES = setOf("the", "a", "an")

    private val APOSTROPHES = setOf('\'', '\u2019', '\u2018', '`', '\u00b4')

    private val SPELLED_OUT: Map<Char, String> = mapOf(
        '\u00df' to "ss", '\u00e6' to "ae", '\u00c6' to "AE", '\u0153' to "oe", '\u0152' to "OE",
        '\u00f8' to "o", '\u00d8' to "O", '\u0142' to "l", '\u0141' to "L", '\u0111' to "d", '\u0110' to "D",
        '\u00fe' to "th", '\u00de' to "TH",
    )

    private val ROMAN: Map<String, String> = listOf(
        "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x", "xi", "xii", "xiii", "xiv", "xv",
        "xvi", "xvii", "xviii", "xix", "xx",
    ).mapIndexed { i, numeral -> numeral to (i + 2).toString() }
        .filter { it.first.length >= 2 }
        .toMap()
}
