package com.plumemu.identify.name

import java.util.Locale

/**
 * Download-site marks in file names: `Title-romsfun`, `Title (USA) - www.emuparadise.me`,
 * `emulatorgames.net - Title (USA)`, `Title - Download - retrostic.com`, `[www.ziperto.com]`,
 * `(cdromance)`, `{www.loveroms.com}`, `[Download from romsmania.cc]`.
 *
 * A mark is a domain (letters and digits, a dot, one of [TLDS]; no spaces) or one of the
 * [KNOWN] bare site names. Outside brackets it is taken only at the start or the end of the name
 * and only when attached by `-`, `_` or ` - ` (a domain may also follow a plain space), so dots
 * inside real titles ("Dr. Mario", "Vol. 1", "I.Q Mania") are never read as one.
 */
internal object SiteMarks {
    /**
     * Sites whose bare name people's files carry (the synthetic corpus's `SITES`, from real
     * collections). Lower case; matched ignoring case. Add a name only when it is seen in files and
     * cannot be a word of a game title.
     */
    val KNOWN: List<String> = listOf(
        "coolrom", "romsfun", "romsmania", "emuparadise", "ziperto", "vimm's lair", "vimms lair", "vimm",
        "romspure", "cdromance", "romsgames", "romulation", "loveroms", "retrostic", "edgeemu", "wowroms",
        "doperoms", "emulatorgames", "romspedia", "romhustler", "gamulator", "freeroms", "nicoblog",
    )

    /** Top-level domains download sites use. A dot before anything else is not a domain. */
    const val TLDS = "com|net|org|cc|me|io|co|info|to|ws|biz|xyz|club|site|online|ru|de|fr|it|es|pl|nl|uk|us|eu|tv|gg|games"

    /**
     * Labels of letters and digits only: a hyphen would let "Tetris DX-romsfun.com" read "DX-romsfun"
     * as the domain and cut "DX" (none of the sites people's files name has one).
     */
    private const val DOMAIN = """(?:https?://)?(?:www\.)?[a-z0-9]+(?:\.[a-z0-9]+)*\.(?:$TLDS)/?"""
    private val knownAlternatives = KNOWN.joinToString("|") { Regex.escape(it) }
    private const val DOWNLOAD = """(?:download\s*[-_]\s*)?"""

    private val domain = Regex(DOMAIN, RegexOption.IGNORE_CASE)
    private val downloadFrom = Regex("""^(?:download(?:ed)?\s+)?from\s+""", RegexOption.IGNORE_CASE)
    private val trailingDomain = Regex("""(?:\s*[-_]\s*|\s+)$DOWNLOAD$DOMAIN\s*$""", RegexOption.IGNORE_CASE)
    private val trailingKnown = Regex("""\s*[-_]\s*$DOWNLOAD(?:$knownAlternatives)\s*$""", RegexOption.IGNORE_CASE)
    private val leadingDomain = Regex("""^\s*$DOMAIN\s*[-_]\s*""", RegexOption.IGNORE_CASE)
    private val leadingKnown = Regex("""^\s*(?:$knownAlternatives)\s*[-_]\s*""", RegexOption.IGNORE_CASE)

    /** True when a bracket group's [content] is only a site mark (`www.CoolROM.com`, `cdromance`, `Download from x.cc`). */
    fun isSite(content: String): Boolean {
        val text = content.trim().replaceFirst(downloadFrom, "")
        return text.isNotEmpty() && (domain.matches(text) || text.lowercase(Locale.ROOT) in KNOWN)
    }

    /** [name] without the site marks attached at its start or end outside brackets. */
    fun strip(name: String): String {
        // Most names hold neither a dot nor a known site name: skip the patterns (scan time).
        val domains = '.' in name
        val lower = name.lowercase(Locale.ROOT)
        val known = KNOWN.any { it in lower }
        if (!domains && !known) return name
        var text = name
        repeat(MAX_MARKS) {
            var next = text
            if (domains) next = next.replaceFirst(trailingDomain, "").replaceFirst(leadingDomain, "")
            if (known) next = next.replaceFirst(trailingKnown, "").replaceFirst(leadingKnown, "")
            if (next == text || next.isBlank()) return text
            text = next
        }
        return text
    }

    /** A name carries at most a leading and a trailing mark, sometimes two at the end. */
    private const val MAX_MARKS = 3
}
