package com.plumemu.identify.name

/**
 * Whether a disc serial or cartridge code may name a file.
 * A hack or translation keeps the original's serial or code, so the code would name it as the
 * original. The file name wins instead (as with no code) when
 *
 * - the file name carries a hack or translation marker ([hasHackMarker]), or
 * - (cartridge codes only) the cleaned file title and the code's name share a significant word and each has one the
 *   other lacks ([namesDiverge]): "Pokemon Radical Red" against "Pokemon - FireRed Version".
 *
 * A bad file name ("FF7 Disc1", "game") shares nothing or only adds to the code's name, so the code
 * still rescues it. Words are those of [MatchKey.tokens], so spelling is evened out the same way.
 */
object HackNames {
    private val MARKERS = Regex(
        "[\\[(]\\s*hack[^\\])\\[(]*[\\])]" +
            "|(?-i:\\[h(?:[0-9][^\\]]*)?\\])" +
            "|\\[t[+-][^\\]]*\\]" +
            "|[\\[(]\\s*(?:translated|translation)[^\\])\\[(]*[\\])]",
        RegexOption.IGNORE_CASE,
    )

    private val STOPWORDS = setOf(
        "the", "and", "for", "with", "version", "edition", "disc", "disk", "cd", "rev", "game",
        "ver", "special", "collection", "pack",
    )

    /** True when [fileName] has a hack or translation tag. */
    fun hasHackMarker(fileName: String): Boolean = MARKERS.containsMatchIn(fileName)

    /** True when [cleanedTitle] and [codeName] each have a significant word the other lacks and share one. */
    fun namesDiverge(cleanedTitle: String, codeName: String): Boolean {
        val file = significant(cleanedTitle)
        val code = significant(codeName)
        return file.any { it in code } && file.any { it !in code } && code.any { it !in file }
    }

    /**
     * Whether the code's [codeName] must not name [fileName] whose cleaned title is [cleanedTitle].
     * The word rule ([namesDiverge]) applies only when [wordRule] is true: cartridge codes, which
     * a hack copies and which are reached only when no CRC matched. Disc serials, header titles
     * and Switch title ids get the marker alone: their file names are often abbreviated or
     * misspelt ("GTA San Andreas", "Crash Bandicot") and the serial must still rescue them.
     */
    fun rejectsCodeName(fileName: String, cleanedTitle: String?, codeName: String, wordRule: Boolean): Boolean =
        hasHackMarker(fileName) ||
            (wordRule && !cleanedTitle.isNullOrBlank() && namesDiverge(cleanedTitle, codeName))

    private fun significant(title: String): Set<String> =
        MatchKey.tokens(title).filterTo(LinkedHashSet()) { word ->
            word.count { it.isLetter() } >= 3 && !word.all { it.isDigit() } && word !in STOPWORDS
        }
}
