package com.plumemu.home

/** A game title read as its main title and the subtitle after it ("Zone of the Enders" and "The Fist of Mars"); [subtitle] is "" when there is none. */
internal data class SplitTitle(val main: String, val subtitle: String)

/**
 * [title] split at its first subtitle mark (": ", " - ", " – ", " — "), so the details column breaks a long title where it reads, never
 * mid-phrase. No split when either side would be empty or the main part shorter than [MIN_MAIN] letters ("X: Y"); the whole title stays the main.
 */
internal fun splitTitle(title: String): SplitTitle {
    val cut = MARKS.mapNotNull { mark -> title.indexOf(mark).takeIf { it >= 0 }?.let { it to mark } }.minByOrNull { it.first }
        ?: return SplitTitle(title, "")
    val main = title.substring(0, cut.first).trim()
    val subtitle = title.substring(cut.first + cut.second.length).trim()
    return if (main.length < MIN_MAIN || subtitle.isEmpty()) SplitTitle(title, "") else SplitTitle(main, subtitle)
}

private val MARKS = listOf(": ", " - ", " – ", " — ")
private const val MIN_MAIN = 2
