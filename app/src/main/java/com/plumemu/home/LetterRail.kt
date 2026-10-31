package com.plumemu.home

import com.plumemu.identify.name.TitleCleaner
import java.text.Normalizer
import java.util.Locale

/**
 * The letter a game files under: L1 / R1 ([letterJumpTarget]) and the letter rail in the system peek
 * ([railLettersOf]) both use it, so they always agree. Lists are ordered by the games' sort name
 * (identify's `TitleCleaner.sortName`: no case, no accents, no leading "The" / "A" / "An", no leading symbols),
 * so the letter is the first character of that, and a digit, symbol or blank is [SYMBOL_KEY]:
 * "The Legend of Zelda" is under L. A row without a sort name (the fakes) gets one from its title the same way.
 */
internal object LetterKey {
    const val SYMBOL_KEY = '#'

    fun of(row: GameRowModel): Char = ofSortName(row.sortName.ifEmpty { TitleCleaner.sortName(row.title) })

    fun ofSortName(sortName: String): Char {
        val first = sortName.firstOrNull() ?: return SYMBOL_KEY
        if (!Character.isLetter(first)) return SYMBOL_KEY
        return first.toString().uppercase(Locale.ROOT).first()
    }

    /**
     * An app's sort name: its label's first letters folded (accents, case) and nothing dropped. The Apps list is
     * ordered by the label alone (launcher `InstalledApps`), so "The Weather" stays under T there.
     */
    fun foldedLabel(label: String): String =
        Normalizer.normalize(label.trim(), Normalizer.Form.NFD).filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }.lowercase(Locale.ROOT)
}

/**
 * Rows at the top of [rows] that sit outside the order: the Apps list pins Android's Settings first, then
 * orders by label, so the rail would start with its letter. At most one row, and only when it is out of order.
 */
internal fun pinnedLeadingRows(rows: List<GameRowModel>): Int =
    if (rows.size > 1 && LetterKey.of(rows[0]) > LetterKey.of(rows[1])) 1 else 0

/** The distinct letters of [rows] in list order, a pinned leading row left out ([pinnedLeadingRows] of the Apps list). */
internal fun railLettersOf(
    rows: List<GameRowModel>,
    skip: Int = 0,
): List<Char> {
    val seen = LinkedHashSet<Char>()
    for (i in skip until rows.size) seen += LetterKey.of(rows[i])
    return seen.toList()
}

/**
 * The letters to draw when only [capacity] fit: all of them when they fit, else [capacity] letters
 * around [focused] (centred, kept inside the list). [focused] null or not in [letters] shows the first ones.
 */
internal fun railWindow(
    letters: List<Char>,
    focused: Char?,
    capacity: Int,
): List<Char> {
    val room = capacity.coerceAtLeast(1)
    if (letters.size <= room) return letters
    val at = letters.indexOf(focused ?: ' ').coerceAtLeast(0)
    val start = (at - (room - 1) / 2).coerceIn(0, letters.size - room)
    return letters.subList(start, start + room)
}
