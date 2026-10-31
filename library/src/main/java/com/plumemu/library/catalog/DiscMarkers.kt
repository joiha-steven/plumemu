package com.plumemu.library.catalog

import com.plumemu.identify.name.TitleCleaner
import java.util.Locale

/**
 * Disc markers in the forms people write them, rewritten to the No-Intro form `(Disc N)` /
 * `(Disc N of M)` that `TitleCleaner` reads, so the discs of one game group and the title has no
 * marker. The scan's own reading of a name: identify's `TitleCleaner` keeps the round-bracket form
 * only, and names that already carry it are returned unchanged (their disc groups stay as stored).
 *
 * Read as markers, case-insensitive, `Disc`, `Disk` or `CD` with a number of one or two digits:
 * - in square brackets: `[Disc1]`, `[Disc 2]`, `[CD 1]`, `[Disk 1 of 2]`;
 * - at the end of the name (only bracket groups may follow), after a space, `-`, `_` or `.`, with a
 *   number from 1 to 9: `Title - Disc 1`, `Title Disc1`, `Title CD1`, `Title CD 2`, `Title_CD1`,
 *   `Title - Disc 1 (USA)`. Not after a word that makes the disc part of the title
 *   (`Demo Disc 2`, `Bonus Disc 1`, `Music CD 3`), and not with two digits
 *   (`Official U.S. PlayStation Magazine Demo Disc 34` is a title);
 * - the whole name: `disc1`, `Disc 2`, `CD1` (a generic name; the folder gives the title).
 *
 * Not markers: a word without a number (`Sonic CD`), a marker inside the title
 * (`Disc Jockey 2000`), `(Side A)` / `(Side B)` (sides of one floppy or tape, usually in one file:
 * they stay in the variant key, so two sides are two games, as before).
 */
internal object DiscMarkers {
    private const val MARKER = """(?:disc|disk|cd)[\s_]*(\d{1,2})(?:\s*(?:of|/)\s*(\d{1,2}))?"""
    private const val LOOSE_MARKER = """(?:disc|disk|cd)[\s_]*(0?[1-9])(?:\s*(?:of|/)\s*(\d{1,2}))?"""
    private val squareMarker = Regex("""\[\s*$MARKER\s*]""", RegexOption.IGNORE_CASE)
    private val trailingMarker = Regex(
        """^(.*?[^\s\-_.])[\s\-_.]+$LOOSE_MARKER((?:\s*(?:\([^()]*\)|\[[^\[\]]*]))*)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Words before an unbracketed `Disc N` / `CD N` that make it part of the title: a separate
     * release (`Tango Demo Disc 2`, `NekoNeko Fan Disc 2`, `Action Replay Premium CD 2`). Taken from
     * the Redump and No-Intro DATs: every title there that ends in a word and
     * `Disc`/`Disk`/`CD` (DATs write real discs as `(Disc N)`), keeping the words that say what kind
     * of disc it is, not words that also end game titles (`Sonic CD`, `GameCube`, `Street Fighter II
     * Movie CD1` is a real two-disc game: no `movie`, `art`, `system`, `mission`, `cover`, `game`).
     * When in doubt a word stays in: two entries a person can open beat a disc nobody reaches;
     * plus the kinds people name (drama, sound, music, video, making, extra).
     */
    private val titleDiscWords = setOf(
        "demo", "demonstration", "service", "press", "special", "bonus", "sampler", "preview", "fan", "promotion",
        "promotional", "promo", "restore", "recovery", "premium", "data", "start-up", "startup", "asset", "assets",
        "install", "installation", "installer", "setup", "append", "transfer", "tokuten", "otameshi", "playcodes",
        "pre-order", "localization", "enhancement", "kiosk", "trial", "magazine", "scenery", "seller", "selling",
        "drama", "sound", "music", "video", "making", "extra", "extras", "audio", "soundtrack", "addon", "add-on",
        "test", "campaign", "patrol", "hut", "hit", "user",
    )
    private val wholeMarker = Regex("""^\s*$MARKER\s*$""", RegexOption.IGNORE_CASE)

    /** [baseName] (no extension) with its disc marker as `(Disc N)`; unchanged when it has none. */
    fun canonical(baseName: String): String {
        if (TitleCleaner.hasDiscTag(baseName)) return baseName
        squareMarker.find(baseName)?.let { m ->
            return baseName.replaceRange(m.range, tag(m.groupValues[1], m.groupValues[2]))
        }
        wholeMarker.matchEntire(baseName)?.let { m -> return tag(m.groupValues[1], m.groupValues[2]) }
        trailingMarker.matchEntire(baseName)?.let { m ->
            val (title, number, total, rest) = m.destructured
            val lastWord = title.substringAfterLast(' ').substringAfterLast('-').lowercase(Locale.ROOT)
            if (lastWord !in titleDiscWords) return "$title ${tag(number, total)}$rest"
        }
        return baseName
    }

    private fun tag(number: String, total: String): String {
        val n = number.toInt()
        return if (total.isEmpty()) "(Disc $n)" else "(Disc $n of ${total.toInt()})"
    }

    /** Words that name no game (`game.iso`, `disc.gdi`, `Disc 2.chd`): the folder gives the title. */
    private val genericWords = setOf(
        "game", "games", "disc", "disk", "cd", "dvd", "rom", "image", "main", "track", "default", "playlist", "iso",
    )
    private val trailingNumber = Regex("""[\s_\-]*\d{0,3}$""")

    /**
     * Whether [cleanTitle] (a cleaned title, no brackets) names no game. A title of digits only
     * names one (`720`, `007`, `1942`).
     */
    fun isGeneric(cleanTitle: String): Boolean {
        val trimmed = cleanTitle.trim()
        if (trimmed.isNotEmpty() && trimmed.all { it.isDigit() }) return false
        val word = cleanTitle.replace(trailingNumber, "").trim().lowercase(Locale.ROOT)
        return word.isEmpty() || word in genericWords
    }
}
