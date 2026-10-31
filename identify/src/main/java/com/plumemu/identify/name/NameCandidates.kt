package com.plumemu.identify.name

/**
 * The cleanings of one file's title the name matcher tries, conservative first:
 *
 * 1. the cleaned title as [TitleCleaner] gave it (also the fallback when nothing matches);
 * 2. the same with words split where case or digits change inside a word
 *    ("MagicKnightRayearth2" -> "Magic Knight Rayearth 2", "PuyoPuyoTsuu" -> "Puyo Puyo Tsuu");
 * 3. for a name without punctuation, its "The" moved to the front: scene and run-together names
 *    keep the article where the DAT's "Title, The - Subtitle" put it ("Sims 2 The Castaway");
 * 4. on DS and GBA only, without a leading four-digit release number ("1234 - Title"): those
 *    collections number every release, while elsewhere "1942 - ..." is a title. A number with a
 *    leading zero is already gone ([TitleCleaner]).
 *
 * 5. without a leading `1 - `, `01. ` or `001_ ` number of one to three digits (a numbered
 *    collection), after the name as written, so a database still decides.
 *
 * A candidate is accepted only when the matcher finds it in the pack, so an aggressive cleaning
 * never reaches the list unless it names a known game.
 */
internal object NameCandidates {
    private val leadingNumber = Regex("""^\d{1,3}(?:\s+-\s+|\.\s+|_\s+)(.+)$""")
    private val releaseNumber = Regex("""^\d{4}\s+-\s+(.+)$""")

    /** Systems whose collections put a release number in front of every name. */
    private val NUMBERED_SYSTEMS = setOf("nds", "gba")

    fun of(cleanedTitle: String, systemId: String): List<String> {
        val out = ArrayList<String>(3)
        out += cleanedTitle
        val split = splitWords(cleanedTitle)
        split?.let { out += it }
        articleToFront(split ?: cleanedTitle)?.let { out += it }
        if (systemId in NUMBERED_SYSTEMS) {
            releaseNumber.matchEntire(cleanedTitle)?.let { out += it.groupValues[1] }
        }
        // After the title as written: "1942 - ..." or "007 - ..." may be the name a database has.
        leadingNumber.matchEntire(cleanedTitle)?.let { out += it.groupValues[1] }
        return if (out.size == 1) out else out.distinct()
    }

    private val innerThe = Regex("""^(\S.*?) [Tt]he (\S.*)$""")

    /** "Sims 2 The Castaway" -> "The Sims 2 Castaway" when [title] is words and spaces only; else null. */
    fun articleToFront(title: String): String? {
        if (title.any { !it.isLetterOrDigit() && it != ' ' }) return null
        val match = innerThe.matchEntire(title) ?: return null
        return "The ${match.groupValues[1]} ${match.groupValues[2]}"
    }

    /**
     * [title] with a space where a lower-case letter meets an upper-case one, where letters meet
     * digits, and before the last capital of a run followed by a lower-case letter ("XMLParser" ->
     * "XML Parser"); null when there is no such place.
     */
    fun splitWords(title: String): String? {
        var changed = false
        val out = StringBuilder(title.length + 8)
        for (i in title.indices) {
            val c = title[i]
            if (i > 0) {
                val p = title[i - 1]
                val next = title.getOrNull(i + 1)
                val boundary = (p.isLowerCase() && c.isUpperCase()) ||
                    (p.isLetter() && c.isDigit()) || (p.isDigit() && c.isLetter()) ||
                    (p.isUpperCase() && c.isUpperCase() && next != null && next.isLowerCase())
                if (boundary) {
                    out.append(' ')
                    changed = true
                }
            }
            out.append(c)
        }
        return if (changed) out.toString() else null
    }
}
